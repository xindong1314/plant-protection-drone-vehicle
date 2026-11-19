"""上行：QoS 选择、缓存规则、补传顺序/ts 保真/flush 中途失败保留剩余。"""
import json
import threading
import time

from conftest import FakeClock, RecordingSender
from ppdv_comm.store import OfflineCache
from ppdv_comm.uplink import Uplink

DEVICE = "PPDV-0001"
TRACK_TOPIC = "ppdv/v1/PPDV-0001/tele/track"


def make(tmp_path, sender=None, clock=None):
    sender = sender or RecordingSender()
    cache = OfflineCache(str(tmp_path / "cache"), DEVICE)
    up = Uplink(DEVICE, sender, cache, "1.2.0", clock=clock or FakeClock())
    return up, sender, cache


class AckSender(RecordingSender):
    """带 QoS1 PUBACK 等待的假发送端（tele/track 专用）。"""

    def __init__(self, connected=True, ack=True):
        super().__init__(connected=connected)
        self.ack = ack
        self.ack_calls = []   # (topic, payload, qos, retain, timeout_s)

    def publish_wait_ack(self, topic, payload, qos=1, retain=False, timeout_s=3.0):
        self.ack_calls.append((topic, payload, qos, retain, timeout_s))
        if not self.ack:
            return False
        self.published.append((topic, payload, qos, retain))
        return True


def track_data(session_id="TS-1", window_index=0, session_end=False,
               track_state="ACTIVE", area_id=None):
    data = {"trackSessionId": session_id}
    if area_id is not None:
        data["areaId"] = area_id
    data["sessionEnd"] = session_end
    data["windowIndex"] = window_index
    data["tracks"] = [] if session_end else [{
        "trackId": "T-1", "episodeId": "EP-1", "episodeNo": 0,
        "class": "leaf_late_blight", "confidence": 0.5,
        "firstSeenTs": 1, "lastSeenTs": 2, "trackState": track_state,
    }]
    return data


def test_publish_track_envelope_qos_and_no_forbidden_fields(tmp_path):
    up, sender, cache = make(tmp_path, AckSender())
    assert up.publish_track(track_data(area_id=12)) is True
    topic, payload, qos, retain = sender.published[0]
    assert topic == TRACK_TOPIC
    assert qos == 1 and retain is False
    envelope = json.loads(payload)
    assert envelope["type"] == "track"
    assert envelope["deviceId"] == DEVICE
    assert envelope["msgId"] and envelope["ts"] > 0
    assert envelope["data"]["areaId"] == 12
    assert sender.ack_calls[0][4] == 3.0      # 硬编码 3s PUBACK 上限
    assert "frameCount" not in payload and "positionSource" not in payload
    assert cache.size() == 0


def test_publish_track_offline_and_force_cache(tmp_path):
    up, sender, cache = make(tmp_path, AckSender(connected=False))
    assert up.publish_track(track_data()) is False
    assert cache.size() == 1
    assert sender.ack_calls == []

    up2, sender2, cache2 = make(tmp_path / "b", AckSender())
    assert up2.publish_track(track_data(), force_cache=True) is False
    assert cache2.size() == 1
    assert sender2.ack_calls == []


def test_publish_track_ack_timeout_caches_message(tmp_path):
    up, sender, cache = make(tmp_path, AckSender(ack=False))
    assert up.publish_track(track_data()) is False
    assert sender.ack_calls                       # 已发布并入队
    assert cache.size() == 1                      # 未确认 → 转缓存重试
    sender.ack = True
    assert up.flush() == 1
    assert cache.size() == 0


def test_publish_track_ended_is_persisted_then_recycled(tmp_path):
    up, sender, cache = make(tmp_path, AckSender())
    assert up.publish_track(track_data(track_state="ENDED")) is True
    assert cache.size() == 0                      # 确认后回收新副本

    up2, _sender2, cache2 = make(tmp_path / "b", AckSender(ack=False))
    assert up2.publish_track(track_data(track_state="ENDED")) is False
    assert cache2.size() == 1                     # 未确认：ENDED 副本保留


def test_publish_track_replaces_stale_window_before_sending(tmp_path):
    up, sender, cache = make(tmp_path, AckSender(connected=False))
    up.publish_track(track_data("TS-1", 0))
    up.publish_track(track_data("TS-1", 1))
    # 离线期间同会话旧窗口被新窗口替代：缓存只留最新窗口
    entries = cache.read_all()
    assert len(entries) == 1
    assert json.loads(entries[0][1])["data"]["windowIndex"] == 1

    sender.set_connected(True)
    assert up.publish_track(track_data("TS-1", 2)) is True
    assert cache.size() == 0                      # 旧窗口清理 + 新报文确认回收


def test_offline_windows_do_not_grow_cache_linearly(tmp_path):
    """离线连续 10 个窗口只保留最新窗口的分片，不随窗口数线性增长。"""
    up, _sender, cache = make(tmp_path, AckSender(connected=False))
    for window in range(10):
        up.publish_track(track_data("TS-1", window))
    entries = cache.read_all()
    assert [json.loads(payload)["data"]["windowIndex"] for _t, payload in entries] == [9]


def test_publish_track_keeps_other_session_end_and_other_topics(tmp_path):
    up, sender, cache = make(tmp_path, AckSender(connected=False))
    up.publish_track(track_data("TS-1", 0))
    up.publish_track(track_data("TS-OTHER", 0))
    up.publish_track(track_data("TS-1", 9, session_end=True))
    up.publish_work({"flow": 1.0})
    assert cache.size() == 3                      # TS-1 旧窗口被 ending 报文替代

    sender.set_connected(True)
    assert up.publish_track(track_data("TS-1", 10)) is True
    remaining = cache.read_all()
    assert len(remaining) == 3
    sessions = []
    for topic, payload in remaining:
        if topic == TRACK_TOPIC:
            data = json.loads(payload)["data"]
            sessions.append((data["trackSessionId"], data["windowIndex"], data["sessionEnd"]))
        else:
            sessions.append(("work", None, None))
    assert ("TS-OTHER", 0, False) in sessions
    assert ("TS-1", 9, True) in sessions          # sessionEnd=true 永不因合并被删
    assert ("work", None, None) in sessions


def test_publish_track_same_window_shards_are_kept(tmp_path):
    up, _sender, cache = make(tmp_path, AckSender(connected=False))
    up.publish_track(track_data("TS-1", 3))
    up.publish_track(track_data("TS-1", 3))
    assert cache.size() == 2
    assert up.cache.has_stale_track_windows(TRACK_TOPIC, "TS-1", 3) is False


def test_publish_track_oversize_is_dropped(tmp_path, monkeypatch):
    import ppdv_comm.uplink as uplink_module
    monkeypatch.setattr(uplink_module, "TRACK_MAX_BYTES", 64)
    up, sender, cache = make(tmp_path, AckSender())
    assert up.publish_track(track_data()) is False
    assert sender.published == [] and sender.ack_calls == []
    assert cache.size() == 0


def test_publish_track_skips_send_when_persist_fails(tmp_path, monkeypatch):
    """追加失败则不发送、不清理旧窗口（防"旧已删、新未落盘"双丢）。"""
    up, sender, cache = make(tmp_path, AckSender(connected=False))
    up.publish_track(track_data("TS-1", 0))
    sender.set_connected(True)
    monkeypatch.setattr(cache, "append", lambda topic, payload: False)
    assert up.publish_track(track_data("TS-1", 1)) is False
    assert sender.ack_calls == []                 # 没有在线发送
    assert cache.size() == 1                      # 旧窗口未被清理


def test_flush_waits_track_ack_and_keeps_payload(tmp_path):
    up, sender, cache = make(tmp_path, AckSender())
    payload = json.dumps({"msgId": "m-1", "deviceId": DEVICE, "ts": 123,
                          "type": "track", "data": track_data()})
    cache.append(TRACK_TOPIC, payload)

    sender.ack = False
    assert up.flush() == 0
    assert cache.size() == 1

    sender.ack = True
    assert up.flush() == 1
    assert cache.size() == 0
    replayed = [p for (t, p, *_ ) in sender.published if t == TRACK_TOPIC]
    assert replayed == [payload]                  # 补传逐字节保真（msgId/ts 不变）


def test_flush_and_track_send_share_outbound_lock(tmp_path):
    """flush 持出站锁期间 track 发送必须等待：不会出现"新窗口先、旧窗口后"。"""
    up, sender, cache = make(tmp_path, AckSender())
    cache.append(TRACK_TOPIC, json.dumps(
        {"msgId": "old", "deviceId": DEVICE, "ts": 1, "type": "track",
         "data": track_data("TS-1", 0)}))
    cache.append("ppdv/v1/PPDV-0001/tele/state", '{"lat":1.0}')

    gate = threading.Event()
    entered = threading.Event()
    original = sender.publish

    def blocking_publish(topic, payload, qos=0, retain=False):
        if topic.endswith("/tele/state"):
            entered.set()
            gate.wait(2)
        return original(topic, payload, qos, retain)

    sender.publish = blocking_publish
    order = []
    flush_thread = threading.Thread(target=lambda: (up.flush(), order.append("flush")))
    flush_thread.start()
    assert entered.wait(2)

    track_thread = threading.Thread(
        target=lambda: (up.publish_track(track_data("TS-1", 1)), order.append("track")))
    track_thread.start()
    time.sleep(0.1)
    assert order == []                            # 两者都被锁挡住
    gate.set()
    flush_thread.join(5)
    track_thread.join(5)
    assert order == ["flush", "track"]            # 旧窗口补传先完成，再发新窗口
    assert cache.size() == 0


def test_aggregator_to_uplink_end_to_end(tmp_path):
    """聚合器 → Uplink.publish_track 全链路：主题/信封/QoS/结束序列顺序。"""
    from ppdv_comm.track import TrackAggregator

    class MonoClock:
        def __init__(self):
            self.now = 500.0

        def __call__(self):
            return self.now

    epoch = FakeClock()
    mono = MonoClock()
    sender = AckSender()
    up, _sender, cache = make(tmp_path, sender, clock=epoch)
    agg = TrackAggregator(DEVICE, up.publish_track, window_s=2.0,
                          lost_timeout_ms=10000, clock=epoch, monotonic=mono)

    agg.handle_event({"event": "detection", "trackId": "T-1",
                      "class": "leaf_late_blight", "confidence": 0.7,
                      "frameTs": epoch.now_ms})
    mono.now += 2.0
    agg.tick()
    first = json.loads(sender.payloads_of("tele/track")[0])
    assert first["type"] == "track" and first["deviceId"] == DEVICE
    assert first["data"]["sessionEnd"] is False
    assert first["data"]["tracks"][0]["trackState"] == "ACTIVE"

    agg.handle_event({"event": "session_end"})
    payloads = [json.loads(p) for p in sender.payloads_of("tele/track")]
    assert len(payloads) == 3
    assert payloads[1]["data"]["sessionEnd"] is False
    assert payloads[1]["data"]["tracks"][0]["trackState"] == "ENDED"
    assert payloads[2]["data"]["sessionEnd"] is True
    assert payloads[2]["data"]["tracks"] == []
    assert payloads[1]["data"]["trackSessionId"] == payloads[2]["data"]["trackSessionId"]
    assert payloads[1]["data"]["windowIndex"] == payloads[2]["data"]["windowIndex"]
    assert cache.size() == 0


def test_end_sequence_keeps_data_before_end_in_cache(tmp_path):
    """离线结束：缓存顺序必须是"数据分片在前、sessionEnd 在后"（结束序列顺序）。"""
    from ppdv_comm.track import TrackAggregator

    class MonoClock:
        def __init__(self):
            self.now = 800.0

        def __call__(self):
            return self.now

    epoch = FakeClock()
    sender = AckSender(connected=False)
    up, _sender, cache = make(tmp_path, sender, clock=epoch)
    agg = TrackAggregator(DEVICE, up.publish_track, window_s=2.0,
                          lost_timeout_ms=10000, clock=epoch, monotonic=MonoClock())
    agg.handle_event({"event": "detection", "trackId": "T-1",
                      "class": "leaf_mold", "confidence": 0.6,
                      "frameTs": epoch.now_ms})
    agg.handle_event({"event": "session_end"})

    entries = cache.read_all()
    assert len(entries) == 2
    flags = [json.loads(payload)["data"]["sessionEnd"] for _topic, payload in entries]
    assert flags == [False, True]                 # 数据分片在前、结束报文在后
    windows = [json.loads(payload)["data"]["windowIndex"] for _topic, payload in entries]
    assert windows[0] == windows[1]               # 结束序列共用同一个 windowIndex


def qos_map_test(up, sender):
    up.publish_state({"lat": 1.0})
    up.publish_work({"flow": 1.0})
    up.publish_zone("A1")
    up.publish_alarm("LOW_BATTERY", "WARN", "低电量")
    up.publish_progress({"taskId": "T1", "status": "RUNNING", "progress": 1})
    up.publish_ack("c1", 0, "OK")
    up.publish_env({"temperature": 20.0})
    up.publish_online()
    qos_by_topic = {t: q for (t, _p, q, _r) in sender.published}
    return qos_by_topic


def test_qos_selection(tmp_path):
    up, sender, _ = make(tmp_path)
    q = qos_map_test(up, sender)
    assert q["ppdv/v1/PPDV-0001/tele/state"] == 0
    # tele/env QoS1（协议 §3 表；审核修正）
    assert q["ppdv/v1/PPDV-0001/tele/env"] == 1
    assert q["ppdv/v1/PPDV-0001/tele/work"] == 1
    assert q["ppdv/v1/PPDV-0001/tele/zone"] == 1
    assert q["ppdv/v1/PPDV-0001/event/alarm"] == 1
    assert q["ppdv/v1/PPDV-0001/task/progress"] == 1
    assert q["ppdv/v1/PPDV-0001/cmd/ack"] == 1
    assert q["ppdv/v1/PPDV-0001/status"] == 1


def test_status_retained_and_env_not_retained(tmp_path):
    up, sender, _ = make(tmp_path)
    up.publish_online()
    up.publish_env({"temperature": 1.0})
    ret_by_topic = {t: r for (t, _p, _q, r) in sender.published}
    assert ret_by_topic["ppdv/v1/PPDV-0001/status"] is True
    assert ret_by_topic["ppdv/v1/PPDV-0001/tele/env"] is False


def test_offline_cache_rules(tmp_path):
    """status 与 tele/env 不缓存；tele/work、tele/zone、alarm、progress、ack、
    tele/state（QoS0）离线同样进缓存。"""
    up, sender, cache = make(tmp_path, RecordingSender(connected=False))
    up.publish_online()          # status：不缓存
    up.publish_env({"t": 1})     # env：不缓存
    up.publish_state({"lat": 1.0})   # QoS0 state：缓存
    up.publish_work({"flow": 1.0})
    up.publish_zone("A1")
    up.publish_alarm("X", "WARN", "m")
    up.publish_progress({"taskId": "T1", "status": "RUNNING", "progress": 1})
    up.publish_ack("c1", 0, "OK")
    assert sender.published == []
    kinds = [json.loads(p)["type"] for (_t, p) in
             [(t, pl) for (t, pl) in _cache_entries(cache)]]
    assert kinds == ["state", "work", "zone", "alarm", "taskProgress", "ack"]
    assert cache.size() == 6


def test_publish_failure_caches_cacheable(tmp_path):
    sender = RecordingSender(connected=True, fail_topics={"ppdv/v1/PPDV-0001/tele/work"})
    up, _s, cache = make(tmp_path, sender)
    ok = up.publish_work({"flow": 1.0})
    assert ok is False
    assert cache.size() == 1
    up.publish_env({"t": 1})   # env 发送失败也不缓存（丢弃）
    assert cache.size() == 1


def test_flush_order_and_ts_fidelity(tmp_path):
    clock = FakeClock()
    up, sender, cache = make(tmp_path, clock=clock)
    sender.set_connected(False)
    up.publish_state({"lat": 1.0})
    ts_first = clock.now_ms
    clock.advance_ms(5_000)
    up.publish_state({"lat": 2.0})
    ts_second = clock.now_ms
    assert cache.size() == 2
    sender.set_connected(True)
    sent = up.flush()
    assert sent == 2 and cache.size() == 0
    topics = [t for (t, *_ ) in sender.published]
    assert topics == ["ppdv/v1/PPDV-0001/tele/state",
                      "ppdv/v1/PPDV-0001/tele/state",
                      "ppdv/v1/PPDV-0001/event/alarm"]  # 补传 2 条 + COMM_RECOVERED
    # 补传 QoS1
    assert all(q == 1 for (_t, _p, q, _r) in sender.published)
    # ts 为原采集时间，不因补传改变（补传 2 条 + COMM_RECOVERED 告警 1 条）
    ts_list = [json.loads(p)["ts"] for (_t, p, *_ ) in sender.published]
    assert ts_list == [ts_first, ts_second, ts_second]
    # payload 与缓存原文一致（信封 ts 不变；只看补传的 2 条 state）
    lats = [json.loads(p)["data"]["lat"] for (_t, p, *_ ) in sender.published
            if "lat" in json.loads(p)["data"]]
    assert lats == [1.0, 2.0]


def test_flush_partial_failure_keeps_remainder(tmp_path):
    up, sender, cache = make(tmp_path)
    sender.set_connected(False)
    for i in range(4):
        up.publish_state({"lat": float(i)})
    sender.set_connected(True)
    published = []
    state = {"n": 0}

    def flaky(topic, payload, qos=0, retain=False):
        state["n"] += 1
        if state["n"] > 2:
            return False
        published.append(topic)
        return True

    sender.publish = flaky
    sent = up.flush()
    assert sent == 2
    # 剩余 2 条保留在缓存，等下轮再试（COMM_RECOVERED 因补传条数>0 也会发 1 条，
    # 走同一 flaky 失败 → 作为 cacheable 告警也落缓存）
    assert cache.size() == 3
    remain = [json.loads(p)["data"]["lat"] for (_t, p) in _cache_entries(cache)
              if "lat" in json.loads(p)["data"]]
    assert remain == [2.0, 3.0]
    # 恢复后可全部补完
    sender.publish = lambda t, p, qos=0, retain=False: True
    sent2 = up.flush()
    assert sent2 == 3 and cache.size() == 0


def test_flush_empty_and_comm_recovered(tmp_path):
    up, sender, cache = make(tmp_path)
    assert up.flush() == 0  # 空缓存不发 COMM_RECOVERED
    sender.set_connected(False)
    up.publish_state({"lat": 1.0})
    sender.set_connected(True)
    sender.clear()
    up.flush()
    alarms = [json.loads(p) for p in sender.payloads_of("event/alarm")]
    assert len(alarms) == 1
    a = alarms[0]
    assert a["type"] == "alarm"
    assert a["data"]["code"] == "COMM_RECOVERED"
    assert a["data"]["level"] == "INFO"
    assert "补传 1 条" in a["data"]["msg"]


def test_flush_recovered_alarm_fails_does_not_lose_cache_state(tmp_path):
    """COMM_RECOVERED 发送失败会作为一条 cacheable 告警落缓存，属预期行为
    （下轮随新遥测一起补传），不影响已清空的补传队列状态。"""
    up, sender, cache = make(tmp_path)
    sender.set_connected(False)
    up.publish_state({"lat": 1.0})
    sender.set_connected(True)
    sender.fail_topics = frozenset({"ppdv/v1/PPDV-0001/event/alarm"})
    assert up.flush() == 1
    # COMM_RECOVERED 本身失败进缓存，下轮 flush 会重试
    assert cache.size() == 1
    sender.fail_topics = frozenset()
    assert up.flush() == 1   # 补上 COMM_RECOVERED
    assert cache.size() == 0


def test_set_rate_clamp(tmp_path):
    up, _s, _c = make(tmp_path)
    up.set_rate(0)
    assert up.rate == 0.1
    up.set_rate(100)
    assert up.rate == 10.0
    up.set_rate(2.5)
    assert up.rate == 2.5


def _cache_entries(cache):
    return [(t, p) for (t, p) in cache.read_all()]
