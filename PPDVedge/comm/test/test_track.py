"""tele/track 车端聚合：会话/出现/窗口/分片/轨迹/校验/结束序列（聚合规则）。

不依赖 broker/ROS/rclpy：用假 epoch 时钟 + 假单调时钟 + 记录型发送出口。
"""
import json
import subprocess
import sys
import threading
import time

import pytest

from conftest import FakeClock
from ppdv_comm import protocol as P
from ppdv_comm.track import (MAX_PENDING_EPISODES, RETIRED_CLASSES, TRACK_CLASSES,
                             TrackAggregator)


class MonoClock:
    """可手动推进的单调时钟（秒）。"""

    def __init__(self, start=1000.0):
        self.now = start

    def __call__(self):
        return self.now

    def advance(self, seconds):
        self.now += seconds


class RecordingPublisher:
    """假发送出口：记录 (data, force_cache)，可整体成功/失败或按序号覆盖。"""

    def __init__(self, ok=True):
        self.ok = ok
        self.override = None
        self.messages = []

    def __call__(self, data, force_cache=False):
        self.messages.append((data, force_cache))
        if self.override is not None:
            return self.override(len(self.messages), data, force_cache)
        return self.ok

    def datas(self):
        return [data for data, _force in self.messages]

    def forces(self):
        return [force for _data, force in self.messages]


def make(publisher=None, **overrides):
    epoch = FakeClock()
    mono = MonoClock()
    publisher = publisher or RecordingPublisher()
    params = dict(window_s=2.0, lost_timeout_ms=10000, session_idle_s=120,
                  trajectory_sample_ms=500, max_trajectory_points=20,
                  max_tracks_per_message=50)
    params.update(overrides)
    agg = TrackAggregator("PPDV-0001", publisher, clock=epoch, monotonic=mono, **params)
    return agg, publisher, mono, epoch


def det(epoch, track_id="T-1", frame_ts=None, **extra):
    event = {
        "event": "detection",
        "trackId": track_id,
        "class": "leaf_late_blight",
        "confidence": 0.8,
        "frameTs": epoch.now_ms if frame_ts is None else frame_ts,
    }
    event.update(extra)
    return event


def end_event(reason="巡检完成"):
    return {"event": "session_end", "reason": reason}


# ---- A1/A2/A3：基本窗口与聚合字段 ----

def test_first_detection_creates_session_and_window():
    agg, pub, mono, epoch = make()
    agg.handle_event(det(epoch))
    assert pub.messages == []          # 未到窗口时间不发
    mono.advance(2.0)
    agg.tick()
    assert len(pub.messages) == 1
    data, force_cache = pub.messages[0]
    assert force_cache is False
    assert data["trackSessionId"].startswith("TS-PPDV-0001-")
    assert 1 <= len(data["trackSessionId"]) <= 64
    assert data["sessionEnd"] is False
    assert data["windowIndex"] == 0
    assert "areaId" not in data
    assert len(data["tracks"]) == 1
    track = data["tracks"][0]
    assert track["episodeNo"] == 0
    assert track["trackState"] == "ACTIVE"
    assert track["class"] == "leaf_late_blight"
    assert track["firstSeenTs"] == track["lastSeenTs"] == epoch.now_ms
    assert 1 <= len(track["episodeId"]) <= 64


def test_episode_update_keeps_first_and_takes_latest():
    agg, pub, mono, epoch = make()
    first_ts = epoch.now_ms
    agg.handle_event(det(epoch, confidence=0.5, boxX=10, boxY=20, boxW=30, boxH=40))
    epoch.advance_ms(400)
    agg.handle_event(det(epoch, confidence=0.9, **{"class": "leaf_early_blight"},
                         boxX=11, boxY=21, boxW=31, boxH=41))
    mono.advance(2.0)
    agg.tick()
    track = pub.datas()[0]["tracks"][0]
    assert track["firstSeenTs"] == first_ts            # 首帧不变
    assert track["lastSeenTs"] == first_ts + 400       # 末帧推进
    assert track["confidence"] == 0.9                  # 取 max
    assert track["class"] == "leaf_early_blight"       # 取最新
    assert (track["boxX"], track["boxY"], track["boxW"], track["boxH"]) == (11, 21, 31, 41)


def test_out_of_order_sample_is_dropped():
    agg, pub, mono, epoch = make()
    agg.handle_event(det(epoch))
    newest = epoch.now_ms
    agg.handle_event(det(epoch, frame_ts=newest - 1000, confidence=0.99))  # 乱序
    mono.advance(2.0)
    agg.tick()
    track = pub.datas()[0]["tracks"][0]
    assert track["lastSeenTs"] == newest
    assert track["confidence"] == 0.8
    assert agg.stats()["out_of_order"] == 1


# ---- A4/A5/A6/A7：出现生命周期 ----

def test_lost_timeout_ends_episode_once():
    agg, pub, mono, epoch = make(lost_timeout_ms=2000)
    agg.handle_event(det(epoch))
    mono.advance(1.0)
    agg.tick()
    assert pub.messages == []                      # 窗口未到、也未判定丢失

    mono.advance(1.5)                              # 距最后样本 2.5s ≥ 丢失超时 2.0s
    agg.tick()
    first = pub.datas()[0]
    assert first["windowIndex"] == 0
    assert first["tracks"][0]["trackState"] == "ENDED"
    assert agg.stats()["lost_timeouts"] == 1

    mono.advance(2.0)                              # 已确认送达 → 不再重复，会话仍 ACTIVE
    agg.tick()
    assert len(pub.messages) == 1
    assert agg.stats()["lost_timeouts"] == 1


def test_explicit_lost_and_ended_events():
    agg, pub, mono, epoch = make()
    base = epoch.now_ms
    agg.handle_event(det(epoch))
    epoch.advance_ms(600)
    agg.handle_event(det(epoch))
    # 乱序的结束事件：只结束本次出现，不回退 lastSeenTs
    agg.handle_event(det(epoch, frame_ts=base + 200, state="LOST"))
    mono.advance(2.0)
    agg.tick()
    track = pub.datas()[0]["tracks"][0]
    assert track["trackState"] == "ENDED"
    assert track["lastSeenTs"] == base + 600
    assert agg.stats()["out_of_order"] == 1


def test_reappear_creates_new_episode():
    agg, pub, mono, epoch = make()
    agg.handle_event(det(epoch))
    agg.handle_event(det(epoch, state="ENDED"))
    mono.advance(2.0)
    agg.tick()
    first = pub.datas()[0]["tracks"][0]

    epoch.advance_ms(1000)
    agg.handle_event(det(epoch))
    mono.advance(2.0)
    agg.tick()
    second = pub.datas()[1]["tracks"][0]
    assert second["episodeId"] != first["episodeId"]
    assert second["episodeNo"] == first["episodeNo"] + 1
    assert second["firstSeenTs"] == epoch.now_ms
    assert second["trackState"] == "ACTIVE"


def test_two_tracks_are_independent():
    agg, pub, mono, epoch = make()
    agg.handle_event(det(epoch, track_id="T-1"))
    agg.handle_event(det(epoch, track_id="T-2"))
    mono.advance(2.0)
    agg.tick()
    tracks = pub.datas()[0]["tracks"]
    assert [t["trackId"] for t in tracks] == ["T-1", "T-2"]
    assert tracks[0]["episodeId"] != tracks[1]["episodeId"]
    assert [t["episodeNo"] for t in tracks] == [0, 0]     # episodeNo 按 trackId 各自计数


# ---- A8：轨迹抽稀 ----

def test_trajectory_sampling_and_trim():
    agg, pub, mono, epoch = make(max_trajectory_points=2)
    base = epoch.now_ms
    boxes = [(10, 10), (12, 12), (13, 13), (14, 14), (15, 15), (16, 16)]
    for index, (x, y) in enumerate(boxes):
        agg.handle_event(det(epoch, frame_ts=base + index * 600,
                             boxX=x, boxY=y, boxW=5, boxH=5))
    mono.advance(2.0)
    agg.tick()
    trajectory = pub.datas()[0]["tracks"][0]["trajectory"]
    assert len(trajectory) <= 2
    assert trajectory[0] == [10, 10, base]                     # 首点保留
    assert trajectory[-1] == [16, 16, base + 5 * 600]           # 末点保留
    assert all(len(point) == 3 for point in trajectory)


def test_trajectory_sample_interval_replaces_last_point():
    agg, pub, mono, epoch = make()
    base = epoch.now_ms
    agg.handle_event(det(epoch, frame_ts=base, boxX=10, boxY=10, boxW=5, boxH=5))
    agg.handle_event(det(epoch, frame_ts=base + 100, boxX=11, boxY=11, boxW=5, boxH=5))
    agg.handle_event(det(epoch, frame_ts=base + 600, boxX=12, boxY=12, boxW=5, boxH=5))
    mono.advance(2.0)
    agg.tick()
    trajectory = pub.datas()[0]["tracks"][0]["trajectory"]
    # 采样基准是最后一次真正追加点的时间；覆盖末点只改坐标，不改采样时间。
    assert trajectory == [[11, 11, base], [12, 12, base + 600]]


def test_high_rate_frames_keep_multiple_trajectory_points():
    """10Hz 真实帧率下不能退化为单点：每 500ms 追加一个轨迹点。"""
    agg, pub, mono, epoch = make()
    base = epoch.now_ms
    for index in range(30):
        agg.handle_event(det(epoch, frame_ts=base + index * 100,
                             boxX=index, boxY=index, boxW=5, boxH=5))
    mono.advance(2.0)
    agg.tick()
    trajectory = pub.datas()[0]["tracks"][0]["trajectory"]
    assert len(trajectory) > 1
    assert trajectory[0] == [4, 4, base]             # 首采样桶保留在 base，坐标取桶内最新帧
    assert trajectory[-1] == [29, 29, base + 2500]   # 坐标取最新帧，时间取采样点
    assert [point[2] for point in trajectory] == [base + index * 500
                                                  for index in range(6)]


def test_sample_without_bbox_adds_no_trajectory_point():
    agg, pub, mono, epoch = make()
    agg.handle_event(det(epoch))
    agg.handle_event(det(epoch, frame_ts=epoch.now_ms + 600, boxX=10))  # 只有 1 个字段
    mono.advance(2.0)
    agg.tick()
    track = pub.datas()[0]["tracks"][0]
    assert "trajectory" not in track
    assert "boxX" not in track


# ---- A9/A28：分片与空窗口 ----

def test_sharding_51_tracks_shares_window_index():
    agg, pub, mono, epoch = make()
    for index in range(51):
        agg.handle_event(det(epoch, track_id=f"T-{index}"))
    mono.advance(2.0)
    agg.tick()
    assert len(pub.messages) == 2
    first, second = pub.datas()
    assert len(first["tracks"]) == 50 and len(second["tracks"]) == 1
    assert first["windowIndex"] == second["windowIndex"] == 0
    assert all(data["sessionEnd"] is False for data in (first, second))

    mono.advance(2.0)                  # 下一窗口 windowIndex +1
    agg.tick()
    assert pub.datas()[2]["windowIndex"] == 1


def test_empty_window_is_never_sent():
    agg, pub, mono, epoch = make()
    agg.handle_event(det(epoch))
    agg.handle_event(det(epoch, state="ENDED"))
    mono.advance(2.0)
    agg.tick()                          # 唯一一条 ENDED 分片
    assert len(pub.messages) == 1
    mono.advance(2.0)
    agg.tick()                          # 已确认且无活跃出现 → 不发空窗口
    assert len(pub.messages) == 1


# ---- A10/A11/A12：结束序列 ----

def test_end_sequence_order_and_new_session():
    agg, pub, mono, epoch = make()
    agg.handle_event(det(epoch))
    mono.advance(2.0)
    agg.tick()
    session_id = pub.datas()[0]["trackSessionId"]

    epoch.advance_ms(500)
    agg.handle_event(end_event())
    assert len(pub.messages) == 3
    data_msg, end_msg = pub.datas()[1], pub.datas()[2]
    assert data_msg["sessionEnd"] is False
    assert data_msg["windowIndex"] == 1
    assert data_msg["tracks"][0]["trackState"] == "ENDED"
    assert end_msg["sessionEnd"] is True
    assert end_msg["tracks"] == []
    assert end_msg["windowIndex"] == 1              # 与结束序列数据分片同窗
    assert end_msg["trackSessionId"] == session_id
    assert pub.forces() == [False, False, False]    # 数据分片均确认 → 结束报文可在线发

    agg.handle_event(det(epoch))                    # 结束后新出现 → 新会话
    mono.advance(2.0)
    agg.tick()
    new = pub.datas()[3]
    assert new["trackSessionId"] != session_id
    assert new["windowIndex"] == 0


def test_end_sequence_forced_to_cache_when_shard_unacked():
    agg, pub, mono, epoch = make()
    agg.handle_event(det(epoch))
    pub.override = lambda n, data, force: n != 1     # 第一条数据分片未确认
    agg.handle_event(end_event())
    assert len(pub.messages) == 2
    assert pub.forces() == [False, True]
    assert pub.datas()[-1]["sessionEnd"] is True


def test_idle_session_end_and_disable():
    agg, pub, mono, epoch = make(session_idle_s=5)
    agg.handle_event(det(epoch))
    mono.advance(1.0)
    agg.tick()
    assert pub.messages == []                        # 窗口未到、会话未空闲
    mono.advance(4.0)
    agg.tick()
    assert pub.datas()[-1]["sessionEnd"] is True

    agg2, pub2, mono2, epoch2 = make(session_idle_s=0)
    agg2.handle_event(det(epoch2))
    mono2.advance(1000.0)
    agg2.tick()
    assert all(data["sessionEnd"] is False for data in pub2.datas())


def test_armed_only_session_sends_nothing():
    agg, pub, _mono, _epoch = make()
    agg.handle_event({"event": "session_start", "areaId": 3})
    agg.handle_event(end_event())
    assert pub.messages == []
    agg.handle_event(end_event())                    # 重复/无会话 session_end 幂等
    assert pub.messages == []


# ---- A13/A14/A15：校验与类别 ----

def test_invalid_inputs_are_dropped_without_crash():
    agg, pub, mono, epoch = make()
    bad_events = [
        "{not json",
        json.dumps([1, 2, 3]),
        {"event": "unknown"},
        det(epoch, track_id=""),
        det(epoch, track_id="X" * 65),
        det(epoch, **{"class": "虫害"}),
        det(epoch, **{"class": "not_a_class"}),
        det(epoch, confidence=1.5),
        det(epoch, confidence=True),
        det(epoch, frame_ts="1700000000000"),
        det(epoch, frame_ts=0),
        det(epoch, frame_ts=epoch.now_ms + 3_600_001),   # 与接收时刻偏差 > 1h
    ]
    for event in bad_events:
        agg.handle_event(event)
    mono.advance(2.0)
    agg.tick()
    assert pub.messages == []
    stats = agg.stats()
    assert stats["events_bad_json"] == 2
    assert stats["events_unknown"] == 1
    assert stats["samples_rejected"] == 9
    assert stats["episodes_total"] == 0


def test_non_finite_numbers_never_reach_payload():
    """NaN/Infinity 不得穿透校验生成非法 JSON；bbox 非有限时只丢 bbox。"""
    agg, pub, mono, epoch = make()
    agg.handle_event(det(epoch, confidence=float("nan")))
    agg.handle_event(det(epoch, confidence=float("inf")))
    agg.handle_event(det(epoch, boxX=float("nan"), boxY=1, boxW=2, boxH=3))
    mono.advance(2.0)
    agg.tick()
    assert agg.stats()["samples_rejected"] == 2
    datas = pub.datas()
    assert len(datas) == 1
    track = datas[0]["tracks"][0]
    assert "boxX" not in track and "trajectory" not in track
    assert track["confidence"] == 0.8
    json.dumps(datas[0], allow_nan=False)     # 出口 JSON 必须可被严格解析


def test_protocol_dumps_rejects_non_finite():
    with pytest.raises(ValueError):
        P.dumps({"bad": float("nan")})
    with pytest.raises(ValueError):
        P.dumps({"bad": float("inf")})


def test_negative_bbox_is_ignored_but_sample_kept():
    agg, pub, mono, epoch = make()
    agg.handle_event(det(epoch, boxX=-1, boxY=10, boxW=5, boxH=5))
    mono.advance(2.0)
    agg.tick()
    track = pub.datas()[0]["tracks"][0]
    assert "boxX" not in track and "trajectory" not in track


def test_area_id_passthrough_only_when_given():
    agg, pub, mono, epoch = make()
    agg.handle_event({"event": "session_start", "areaId": 12})
    agg.handle_event(det(epoch))
    mono.advance(2.0)
    agg.tick()
    assert pub.datas()[0]["areaId"] == 12

    agg2, pub2, mono2, epoch2 = make()
    agg2.handle_event({"event": "session_start", "areaId": 0})   # 非法值丢弃
    agg2.handle_event(det(epoch2))
    mono2.advance(2.0)
    agg2.tick()
    assert "areaId" not in pub2.datas()[0]


def test_session_start_ignored_while_active():
    agg, pub, mono, epoch = make()
    agg.handle_event({"event": "session_start", "areaId": 7})
    agg.handle_event(det(epoch))
    agg.handle_event({"event": "session_start", "areaId": 9})    # 会话中忽略
    mono.advance(2.0)
    agg.tick()
    assert pub.datas()[0]["areaId"] == 7


def test_class_catalog_matches_cloud():
    assert len(TRACK_CLASSES) == 15
    assert "leaf_bacterial_spot" in TRACK_CLASSES and "flower_bud" in TRACK_CLASSES
    assert RETIRED_CLASSES == {"虫害", "病斑", "萎蔫", "黄化", "斑点"}
    assert not (TRACK_CLASSES & RETIRED_CLASSES)


# ---- A17/A18：异常与并发 ----

def test_pending_episodes_are_bounded():
    """pending 有上界：离线抖动产生大量单次出现时丢最旧并计数。"""
    agg, _pub, _mono, epoch = make()
    for index in range(MAX_PENDING_EPISODES + 5):
        agg.handle_event(det(epoch, track_id=f"T-{index}", state="ENDED"))
    assert len(agg._session.pending) == MAX_PENDING_EPISODES
    assert agg.stats()["pending_dropped"] == 5


def test_window_thread_start_stop_is_idempotent():
    agg, _pub, _mono, _epoch = make(tick_interval_s=0.05)
    calls = []
    agg.tick = lambda: calls.append(1)
    agg.start()
    agg.start()
    deadline = time.monotonic() + 2
    while not calls and time.monotonic() < deadline:
        time.sleep(0.01)
    agg.stop()
    agg.stop()
    assert calls


def test_posted_events_are_processed_by_the_worker_thread():
    """ROS 回调只 post 入队：解析、聚合与窗口发送都在 track 线程完成。"""
    epoch = FakeClock()
    sent = []

    def publish(data, force_cache=False):
        sent.append(data)
        return True

    agg = TrackAggregator("PPDV-0001", publish, window_s=1.0, tick_interval_s=0.05,
                          clock=epoch)
    agg.start()
    try:
        agg.post(json.dumps(det(epoch)))
        deadline = time.monotonic() + 2
        while not sent and time.monotonic() < deadline:
            time.sleep(0.01)
        assert agg.stats()["events_ok"] == 1
    finally:
        agg.shutdown()
    assert sent and sent[0]["tracks"][0]["trackId"] == "T-1"
    assert agg._thread is None


def test_publish_exception_does_not_break_tick():
    agg, pub, mono, epoch = make(publisher=RecordingPublisher())
    agg.handle_event(det(epoch))

    pub.override = lambda n, data, force: (_ for _ in ()).throw(RuntimeError("boom"))
    mono.advance(2.0)
    agg.tick()
    assert agg.stats()["send_errors"] == 1
    assert agg.stats()["messages_failed"] == 1

    pub.override = None
    mono.advance(2.0)
    agg.tick()                                    # 异常后仍能继续发送
    assert len(pub.messages) == 2


def test_concurrent_events_and_ticks_are_consistent():
    agg, pub, mono, epoch = make()
    errors = []

    def producer():
        try:
            for index in range(200):
                agg.handle_event(det(epoch, track_id=f"T-{index % 7}",
                                     frame_ts=epoch.now_ms + index))
        except Exception as exc:      # pragma: no cover - 断言用
            errors.append(exc)

    def ticker():
        try:
            for index in range(200):
                if index % 20 == 0:
                    mono.advance(0.5)
                agg.tick()
        except Exception as exc:      # pragma: no cover - 断言用
            errors.append(exc)

    threads = [threading.Thread(target=producer), threading.Thread(target=ticker)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    assert errors == []
    agg.shutdown()

    for data in pub.datas():          # 同一报文内不允许重复 episodeId（跨窗口可重复携带）
        ids = [track["episodeId"] for track in data["tracks"]]
        assert len(ids) == len(set(ids))
        assert len(data["tracks"]) <= 50
    assert agg.stats()["messages_sent"] + agg.stats()["messages_failed"] == len(pub.messages)


# ---- A16/A21 相关：配置与报文约束 ----

def test_config_defaults_disable_track_and_version_1_3():
    from ppdv_comm.config import CommConfig
    cfg = CommConfig()
    assert cfg.track_enabled is False
    assert cfg.fw == "1.3.0"
    assert cfg.ros_topic_track_events == "/ppdv/perception/track_events"


def test_payload_has_no_forbidden_fields():
    agg, pub, mono, epoch = make()
    agg.handle_event({"event": "session_start", "areaId": 5})
    agg.handle_event(det(epoch, boxX=1, boxY=2, boxW=3, boxH=4))
    mono.advance(2.0)
    agg.tick()
    agg.handle_event(end_event())

    forbidden = {"frameCount", "lat", "lon", "positionSource", "severity",
                 "inspectionId", "imageRef"}

    def walk(node):
        if isinstance(node, dict):
            assert not (set(node) & forbidden), node
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for item in node:
                walk(item)

    walk(pub.datas())


def test_track_module_imports_without_ros():
    code = ("import sys, ppdv_comm.track; "
            "assert 'rclpy' not in sys.modules; assert 'paho' not in sys.modules")
    completed = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True,
                               cwd=str(__import__("pathlib").Path(__file__).resolve().parents[1]))
    assert completed.returncode == 0, completed.stderr


def test_long_device_id_truncates_session_id():
    epoch = FakeClock()
    mono = MonoClock()
    pub = RecordingPublisher()
    agg = TrackAggregator("D" * 100, pub, window_s=2.0, clock=epoch, monotonic=mono)
    agg.handle_event(det(epoch))
    mono.advance(2.0)
    agg.tick()
    session_id = pub.datas()[0]["trackSessionId"]
    assert len(session_id) <= 64
    assert session_id.startswith("TS-")
