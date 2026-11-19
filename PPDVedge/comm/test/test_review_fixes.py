"""审核修正补充用例：
- LWT 含 msgId（协议 §2 信封必填，v0.2 起）；
- publish_offline（retained、不缓存）；
- engine 补传先于快照早退（有缓存但无快照也要 flush）；
- engine 定位门控（lat/lon 缺失不上报 state，不造 (0,0)）；
- RosStateSource 合并式快照（防撕裂、work 先于 state 不产生 (0,0)）；
- OfflineCache 全方法持锁与坏行（补锁后回归）。
"""
import json

from conftest import FakeClock, RecordingSender
from ppdv_comm.engine import TelemetryEngine
from ppdv_comm.mqtt_link import EdgeMqttClient, MsgIdDeduper
from ppdv_comm.sources.base import VehicleSnapshot
from ppdv_comm.sources.ros_state import RosStateSource
from ppdv_comm.store import OfflineCache
from ppdv_comm.uplink import Uplink

DEVICE = "PPDV-0001"


class MinimalClient(EdgeMqttClient):
    """绕开 paho 构造（单测无 broker/网络），只测回调与字段。"""

    def __init__(self, log=None, trace=None):
        self.device_id = DEVICE
        self._log = log or (lambda m: None)
        self._trace = trace or (lambda m: None)
        self._on_downlink = lambda k, e: None
        self.prefix = "ppdv/v1/" + DEVICE + "/"
        self._deduper = MsgIdDeduper()


def test_lwt_payload_contains_msgid():
    """LWT 遗嘱载荷含 msgId（§2 信封必填，v0.2 起；审核修正）。"""
    import paho.mqtt.client as mqtt
    wills = {}
    orig_will_set = mqtt.Client.will_set

    def spy_will_set(self, topic, payload=None, qos=0, retain=False):
        wills["topic"] = topic
        wills["payload"] = payload

    mqtt.Client.will_set = spy_will_set
    try:
        # 真实构造器（不 start 不连网）：will_set 在 __init__ 内被调用
        EdgeMqttClient(DEVICE, "secret", "localhost", 1883, on_downlink=lambda k, e: None)
    finally:
        mqtt.Client.will_set = orig_will_set
    assert wills["topic"] == "ppdv/v1/PPDV-0001/status"
    env = json.loads(wills["payload"])
    assert list(env.keys()) == ["msgId", "deviceId", "ts", "type", "data"]
    assert isinstance(env["msgId"], str) and len(env["msgId"]) == 36
    assert env["data"] == {"online": False}


def test_publish_offline_retained_not_cached(tmp_path):
    sender = RecordingSender(connected=False)   # 离线也不缓存 status
    up = Uplink(DEVICE, sender, OfflineCache(str(tmp_path / "c"), DEVICE),
                "1.2.0", clock=FakeClock())
    ok = up.publish_offline()
    assert ok is False
    assert sender.published == []
    assert up.cache_size() == 0                 # status 不缓存（防陈旧 retained）
    sender.set_connected(True)
    ok = up.publish_offline()
    assert ok is True
    topic, payload, qos, retain = sender.published[0]
    assert topic.endswith("/status") and qos == 1 and retain is True
    assert json.loads(payload)["data"] == {"online": False}


def make_engine(tmp_path, connected=True):
    sender = RecordingSender(connected=connected)
    uplink = Uplink(DEVICE, sender, OfflineCache(str(tmp_path / "c"), DEVICE),
                    "1.2.0", clock=FakeClock())
    return uplink, sender


class FakeSource:
    def __init__(self):
        self.snapshot = None
        self.zone = None
        self.alarms = []

    def poll_snapshot(self):
        return self.snapshot

    def take_zone_change(self):
        z, self.zone = self.zone, None
        return z

    def take_alarms(self):
        a, self.alarms = self.alarms, []
        return a


def test_flush_happens_even_without_snapshot(tmp_path):
    """审核修正：MQTT 在线但源无快照时，缓存也必须补传（不因快照早退饿死）。"""
    uplink, sender = make_engine(tmp_path, connected=False)
    source = FakeSource()
    engine = TelemetryEngine(source, uplink)
    uplink.publish_state({"lat": 1.0})          # 离线缓存 1 条
    sender.set_connected(True)
    # 源无快照（source.snapshot 为 None）
    engine.tick_round()
    msgs = [(t, json.loads(p)["type"]) for (t, p, *_ ) in sender.published]
    assert ("ppdv/v1/PPDV-0001/tele/state", "state") in msgs   # 补传
    assert any(k == "alarm" for _t, k in msgs)                 # COMM_RECOVERED
    assert uplink.cache_size() == 0


def test_missing_position_no_state_no_zero_fill(tmp_path):
    """定位缺失（None）时不上报 state，也不回填 (0,0)。"""
    uplink, sender = make_engine(tmp_path)
    source = FakeSource()
    engine = TelemetryEngine(source, uplink)
    source.snapshot = VehicleSnapshot(lat=None, lon=None, mode="MANUAL", battery=80)
    engine.tick_round()
    assert sender.payloads_of("tele/state") == []
    # work/progress 也不发（无定位语境）
    assert sender.payloads_of("tele/work") == []
    assert sender.payloads_of("task/progress") == []


def test_missing_position_side_channels_still_drained(tmp_path):
    """定位缺失时 zone/alarm 侧信道照常排空。"""
    uplink, sender = make_engine(tmp_path)
    source = FakeSource()
    engine = TelemetryEngine(source, uplink)
    source.snapshot = VehicleSnapshot(lat=None, lon=None)
    source.zone = "A1"
    source.alarms = [("LOW_BATTERY", "WARN", "低电量", None, None)]
    engine.tick_round()
    assert len(sender.payloads_of("tele/zone")) == 1
    assert len(sender.payloads_of("event/alarm")) == 1


def test_ros_source_work_before_state_no_zero_snapshot():
    """ros 源：work 先于 state 到达时暂存为占位快照（lat/lon=None），
    引擎不会上报 (0,0)；state 到达后合并出完整快照。"""
    src = RosStateSource()
    src.on_work({"spraying": True, "flow": 2.4})
    snap = src.poll_snapshot()
    assert snap.spraying is True and snap.flow == 2.4
    assert not snap.has_position()              # 无定位 → 引擎跳过 state
    src.on_state(VehicleSnapshot(lat=30.1, lon=120.6, mode="AUTO"))
    snap = src.poll_snapshot()
    assert snap.has_position() and snap.spraying is True and snap.flow == 2.4


def test_ros_source_progress_merge_preserves_fields():
    src = RosStateSource()
    src.on_state(VehicleSnapshot(lat=30.1, lon=120.6, mode="AUTO", battery=88))
    src.on_progress({"taskId": "T1", "taskStatus": "RUNNING", "progress": 42})
    snap = src.poll_snapshot()
    assert snap.lat == 30.1 and snap.battery == 88      # state 字段未被 progress 覆盖
    assert snap.task_id == "T1" and snap.progress == 42


def test_ros_source_snapshot_immutable_no_tear():
    """合并生成新对象：旧引用不受后续合并影响（防撕裂）。"""
    src = RosStateSource()
    src.on_state(VehicleSnapshot(lat=1.0, lon=2.0))
    first = src.poll_snapshot()
    src.on_work({"spraying": True})
    assert first.spraying is False            # 旧快照未被就地修改
    assert src.poll_snapshot().spraying is True


def test_ros_source_rejects_downlink():
    """ros 源 apply_command/apply_spray 注入后返回拒绝（ack code=1 语义）。"""
    src = RosStateSource()
    ok, msg = src.apply_command("START", None)
    assert (ok, "拒绝" in msg) == (False, True)
    ok, msg = src.apply_spray("start", {})
    assert (ok, "拒绝" in msg) == (False, True)


def test_cache_read_all_and_size_locked_regression(tmp_path):
    """补锁后回归：append/read_all/size/clear 行为不变。"""
    cache = OfflineCache(str(tmp_path / "c"), DEVICE)
    cache.append("t1", '{"a": 1}')
    cache.append("t2", '{"b": 2}')
    assert cache.size() == 2
    assert [t for (t, _p) in cache.read_all()] == ["t1", "t2"]
    cache.clear()
    assert cache.size() == 0


def test_cache_io_error_no_raise(tmp_path):
    """缓存 IO 异常兜底：目录为文件时 append 不抛异常（记日志）。"""
    bad_dir = tmp_path / "not-a-dir"
    bad_dir.write_text("x")   # 让 mkdir(parents=True) 与 append 失败
    logs = []
    cache = OfflineCache(str(bad_dir / "sub"), DEVICE, log=logs.append)
    cache.append("t", '{"a": 1}')           # 不应抛 OSError
    assert cache.read_all() == []
