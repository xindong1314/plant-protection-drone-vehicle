"""遥测引擎：轮次顺序、work 条件、zone 变化、progress 去重键、缓存补传优先。"""
from conftest import FakeClock, RecordingSender
from ppdv_comm.engine import ProgressKey, TelemetryEngine
from ppdv_comm.sources.base import VehicleSnapshot
from ppdv_comm.store import OfflineCache
from ppdv_comm.uplink import Uplink

DEVICE = "PPDV-0001"


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


def make(tmp_path, connected=True):
    sender = RecordingSender(connected=connected)
    uplink = Uplink(DEVICE, sender, OfflineCache(str(tmp_path / "cache"), DEVICE),
                    "1.2.0", clock=FakeClock())
    source = FakeSource()
    engine = TelemetryEngine(source, uplink)
    return engine, source, uplink, sender


def kinds(sender):
    return [json_loads(p)["type"] for (_t, p, *_ ) in sender.published]


def json_loads(s):
    import json
    return json.loads(s)


def test_state_published_each_round(tmp_path):
    engine, source, _u, sender = make(tmp_path)
    source.snapshot = VehicleSnapshot(lat=30.1, lon=120.6, mode="AUTO", battery=80)
    engine.tick_round()
    assert kinds(sender) == ["state"]
    state = json_loads(sender.payloads_of("tele/state")[0])
    assert state["type"] == "state"
    assert state["data"]["lat"] == 30.1 and state["data"]["lon"] == 120.6
    assert state["data"]["mode"] == "AUTO" and state["data"]["battery"] == 80


def test_no_snapshot_no_publish(tmp_path):
    engine, source, _u, sender = make(tmp_path)
    engine.tick_round()
    assert sender.published == []


def test_work_only_when_spraying_or_running(tmp_path):
    engine, source, _u, sender = make(tmp_path)
    source.snapshot = VehicleSnapshot(lat=1.0, lon=1.0, spraying=False,
                                      task_status="PAUSED", task_id="T1")
    engine.tick_round()
    assert "work" not in kinds(sender)
    source.snapshot = source.snapshot.with_updates(spraying=True)   # frozen: replace
    engine.tick_round()
    assert "work" in kinds(sender)
    sender.clear()
    source.snapshot = source.snapshot.with_updates(spraying=False, task_status="RUNNING")
    engine.tick_round()
    assert "work" in kinds(sender)


def test_zone_only_on_change(tmp_path):
    engine, source, _u, sender = make(tmp_path)
    source.snapshot = VehicleSnapshot(lat=1.0, lon=1.0)
    source.zone = "AREA-1"
    engine.tick_round()
    zone_msgs = sender.payloads_of("tele/zone")
    assert len(zone_msgs) == 1
    assert json_loads(zone_msgs[0])["data"]["zoneCode"] == "AREA-1"
    engine.tick_round()  # 无变化不重发
    assert len(sender.payloads_of("tele/zone")) == 1


def test_progress_dedup_key(tmp_path):
    engine, source, _u, sender = make(tmp_path)
    source.snapshot = VehicleSnapshot(lat=1.0, lon=1.0, task_id="T1",
                                      task_status="RUNNING", progress=10)
    engine.tick_round()
    engine.tick_round()  # 相同 (taskId,status,progress) 不重发
    assert len(sender.payloads_of("task/progress")) == 1
    source.snapshot = source.snapshot.with_updates(progress=11)
    engine.tick_round()
    assert len(sender.payloads_of("task/progress")) == 2


def test_progress_requires_task_id(tmp_path):
    engine, source, _u, sender = make(tmp_path)
    source.snapshot = VehicleSnapshot(lat=1.0, lon=1.0, task_status="RUNNING")
    engine.tick_round()
    assert "taskProgress" not in kinds(sender)


def test_flush_happens_before_new_state(tmp_path):
    """恢复后先补传缓存（旧数据在前），再发本轮 state。"""
    engine, source, uplink, sender = make(tmp_path, connected=False)
    uplink.publish_state({"lat": 0.0})   # 离线缓存
    sender.set_connected(True)
    source.snapshot = VehicleSnapshot(lat=1.0, lon=1.0)
    engine.tick_round()
    msgs = kinds(sender)
    assert msgs[0] == "state"           # 缓存补传的 state 在前
    assert msgs[-1] == "state"          # 本轮 state 在后
    assert "alarm" in msgs              # COMM_RECOVERED
    assert uplink.cache_size() == 0


def test_alarm_drained(tmp_path):
    engine, source, _u, sender = make(tmp_path)
    source.snapshot = VehicleSnapshot(lat=1.0, lon=1.0)
    source.alarms = [("LOW_BATTERY", "WARN", "低电量", 1.0, 2.0)]
    engine.tick_round()
    alarms = [json_loads(p) for p in sender.payloads_of("event/alarm")]
    assert alarms[0]["data"]["code"] == "LOW_BATTERY"
    assert alarms[0]["data"]["lat"] == 1.0 and alarms[0]["data"]["lon"] == 2.0
    engine.tick_round()  # 取空后不再发
    assert len(sender.payloads_of("event/alarm")) == 1


def test_progress_key_equality():
    assert ProgressKey("T1", "RUNNING", 10) == ProgressKey("T1", "RUNNING", 10)
    assert ProgressKey("T1", "RUNNING", 10) != ProgressKey("T1", "PAUSED", 10)
    assert hash(ProgressKey("T1", "RUNNING", 10)) == hash(ProgressKey("T1", "RUNNING", 10))
