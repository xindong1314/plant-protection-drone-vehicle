"""tele/track 车端聚合：内部跟踪事件 → 巡检会话（trackSessionId）→ 出现（episode）→ 周期窗口。

对齐 docs/protocol.md §5.6（v0.4 身份模型）：

- 会话：session_start 只登记 areaId，首条合法 detection 才真正建会话；session_end /
  空闲超时 / 关停结束会话，结束后 trackSessionId 永不复用；会话结束只发
  tracks=[] + sessionEnd=true（不挂在数据分片上）。
- 出现（episode）：同一 trackId 从进入画面到离开为一次出现；结束后再次出现必须换新
  episodeId；trackState 只发 ACTIVE/ENDED；已结束的 episode 只有在包含它的分片确认
  （QoS1 PUBACK）后才移出待发送集合，离线/超时期间允许后续窗口重复携带（云端幂等）。
- 窗口：默认 2s，由独立线程按 500ms 驱动（不接入遥测轮次，也不占用 ROS 执行器）；同窗分片共用 windowIndex，
  tracks 每片 ≤50；空窗口（tracks=[] + sessionEnd=false）绝不发送。

本模块纯 Python：不 import rclpy / paho；单调时钟（窗口/超时）、epoch 时钟（报文）、
发送出口、日志全部注入，便于在无 ROS 环境单测。
"""
import json
import math
import queue
import threading
import time

from .config import (TRACK_LOST_TIMEOUT_MS_RANGE, TRACK_MAX_TRAJECTORY_POINTS_RANGE,
                     TRACK_MAX_TRACKS_PER_MESSAGE_RANGE, TRACK_SESSION_IDLE_S_RANGE,
                     TRACK_TICK_INTERVAL_S_RANGE, TRACK_TRAJECTORY_SAMPLE_MS_RANGE,
                     TRACK_WINDOW_S_RANGE, clamp)

# 与云平台 TrackClassCatalog 同步的 15 类规范 key（6 物候 + 9 病虫害）。
# 注意：该清单是云平台的临时常量（待 Q1 类别注册表替换），改动须与云端同步。
TRACK_CLASSES = frozenset((
    "fruit_immature", "fruit_half_ripe", "fruit_ripe",
    "flower_bud", "flower_anthesis", "flower_post_anthesis",
    "leaf_early_blight", "leaf_late_blight", "leaf_miner", "leaf_mold",
    "leaf_mosaic_virus", "leaf_septoria", "leaf_spider_mites",
    "leaf_yellow_leaf_curl_virus", "leaf_bacterial_spot",
))

# 旧 5 类中文名：云端 RETIRED_CLASS 拒识，车端本地拒发并计数。
RETIRED_CLASSES = frozenset(("虫害", "病斑", "萎蔫", "黄化", "斑点"))

ACTIVE = "ACTIVE"
ENDED = "ENDED"

_IDLE, _ARMED, _ACTIVE = "IDLE", "ARMED", "ACTIVE"

# frameTs 与接收时刻允许的最大偏差（超出视为非法，不伪造时间从接收时刻顶替）。
_MAX_TS_SKEW_MS = 60 * 60 * 1000
# 同一原因 WARN 的最小间隔（秒）
_WARN_INTERVAL_S = 10.0

# 待发送已结束出现的上限：离线抖动时旧 episode 只在 PUBACK 后移除，
# 无上界会让每窗全量重发与缓存重写退化为 O(N²)；超出丢最旧并计数。
MAX_PENDING_EPISODES = 200
# 事件队列上限（对齐 ROS 订阅队列深度 200）：回调只入队，满了丢最新。
TRACK_EVENT_QUEUE_MAX = 200


def _noop(msg):
    pass


def _is_number(value) -> bool:
    """有限数值且非布尔（Python 里 bool 是 int 子类，必须显式排除）。

    NaN/Infinity 必须拒绝：NaN 参与比较恒为 False，会穿透 0~1 等范围校验，
    最终生成云端无法解析的裸 NaN JSON。
    """
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return False
    if isinstance(value, float):
        return math.isfinite(value)
    return True


def _is_int(value) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _thin_trajectory(points):
    """保留首末点的隔点抽稀：删下标为奇数者；末点被删则追加原末点。"""
    kept = points[::2]
    if kept and kept[-1] is not points[-1]:
        kept.append(points[-1])
    return kept


def _trim_trajectory(points, limit):
    """发送前把轨迹抽稀到 ≤limit 点（首末点保留）。"""
    if limit <= 0 or len(points) <= limit:
        return list(points)
    out = list(points)
    while len(out) > limit:
        out = _thin_trajectory(out)
    return out


class _Sample:
    """一条已校验的 detection 样本。"""

    __slots__ = ("track_id", "class_key", "confidence", "frame_ts",
                 "box", "image_w", "image_h", "end_state")

    def __init__(self, track_id, class_key, confidence, frame_ts,
                 box, image_w, image_h, end_state):
        self.track_id = track_id
        self.class_key = class_key
        self.confidence = confidence
        self.frame_ts = frame_ts
        self.box = box
        self.image_w = image_w
        self.image_h = image_h
        self.end_state = end_state


class _Episode:
    """一次出现（episode）的聚合字段：首末时间、max 置信度、最新 bbox/类别/轨迹。"""

    __slots__ = ("track_id", "episode_id", "episode_no", "episode_seq", "class_key",
                 "confidence", "first_seen_ts", "last_seen_ts", "box", "image_w",
                 "image_h", "trajectory", "state", "last_seen_mono",
                 "last_append_ts")

    def __init__(self, track_id, episode_id, episode_no, episode_seq, sample, now_mono):
        self.track_id = track_id
        self.episode_id = episode_id
        self.episode_no = episode_no
        self.episode_seq = episode_seq
        self.class_key = sample.class_key
        self.confidence = sample.confidence
        self.first_seen_ts = sample.frame_ts
        self.last_seen_ts = sample.frame_ts
        self.box = None
        self.image_w = None
        self.image_h = None
        self.trajectory = []
        self.state = ACTIVE
        self.last_seen_mono = now_mono
        self.last_append_ts = None   # 最后一次真正追加轨迹点的时间（采样基准）

    def add_sample(self, sample, now_mono, sample_ms, max_points):
        """同一 episode 内更新：lastSeen 推进、置信度取 max、类别/bbox 取最新。"""
        self.last_seen_ts = sample.frame_ts
        self.last_seen_mono = now_mono
        if sample.confidence > self.confidence:
            self.confidence = sample.confidence
        self.class_key = sample.class_key
        if sample.image_w is not None:
            self.image_w = sample.image_w
        if sample.image_h is not None:
            self.image_h = sample.image_h
        if sample.box is not None:
            self.box = sample.box
            self._push_point(sample.box, sample.frame_ts, sample_ms)
            cap = 2 * max_points
            while len(self.trajectory) > cap:
                self.trajectory = _thin_trajectory(self.trajectory)

    def _push_point(self, box, frame_ts, sample_ms):
        """轨迹采样：距最后一次真正追加点不足间隔时只覆盖末点坐标。

        采样基准是 last_append_ts，不是轨迹末点自身的时间戳：覆盖末点时
        不改基准，否则事件频率高于采样间隔（如 10~30 FPS）时基准随每帧
        滚动，轨迹会永久收敛为 1 个点。
        """
        x, y = box[0], box[1]
        if not self.trajectory:
            self.trajectory.append([x, y, frame_ts])
            self.last_append_ts = frame_ts
            return
        if self.last_append_ts is None:
            self.last_append_ts = self.trajectory[-1][2]
        if frame_ts - self.last_append_ts < sample_ms:
            last = self.trajectory[-1]
            last[0], last[1] = x, y
        else:
            self.trajectory.append([x, y, frame_ts])
            self.last_append_ts = frame_ts

    def to_payload(self, max_points):
        """报文内 track 对象；bbox/尺寸/轨迹缺省时整键省略（云端不覆盖已有值）。"""
        payload = {
            "trackId": self.track_id,
            "episodeId": self.episode_id,
            "episodeNo": self.episode_no,
            "class": self.class_key,
            "confidence": self.confidence,
            "firstSeenTs": self.first_seen_ts,
            "lastSeenTs": self.last_seen_ts,
        }
        if self.box is not None:
            payload["boxX"], payload["boxY"], payload["boxW"], payload["boxH"] = self.box
        if self.image_w is not None:
            payload["imageWidth"] = self.image_w
        if self.image_h is not None:
            payload["imageHeight"] = self.image_h
        trajectory = _trim_trajectory(self.trajectory, max_points)
        if trajectory:
            payload["trajectory"] = [list(point) for point in trajectory]
        payload["trackState"] = self.state
        return payload


class _Session:
    """一次巡检/跟踪会话（报文容器）；trackSessionId 唯一且不复用。"""

    __slots__ = ("session_id", "area_id", "seq", "epoch_ms", "window_index",
                 "episode_seq", "episode_no", "active", "pending",
                 "last_detection_mono", "last_window_mono", "created_mono",
                 "windows", "messages", "episodes", "rejected")

    def __init__(self, session_id, area_id, seq, epoch_ms, now_mono):
        self.session_id = session_id
        self.area_id = area_id
        self.seq = seq
        self.epoch_ms = epoch_ms
        self.window_index = 0
        self.episode_seq = 0
        self.episode_no = {}
        self.active = {}        # trackId -> _Episode
        self.pending = []       # 已 ENDED、尚未确认送达的 _Episode
        self.last_detection_mono = now_mono
        self.last_window_mono = now_mono
        self.created_mono = now_mono
        self.windows = 0
        self.messages = 0
        self.episodes = 0
        self.rejected = 0


class _Message:
    """一条待发报文：data 为 data 级 payload，ended 为该片内置 ENDED 的 episode。"""

    __slots__ = ("data", "ended", "session")

    def __init__(self, data, ended, session):
        self.data = data
        self.ended = ended
        self.session = session


class TrackAggregator:
    """车端跟踪聚合器：事件输入 → 会话/出现状态机 → 窗口分片 → 发送出口。

    发送出口签名：publish(data: dict, force_cache: bool = False) -> bool，
    返回 True 表示已确认送达（生产为 Uplink.publish_track，需 QoS1 PUBACK）。
    """

    def __init__(self, device_id, publish, window_s=2.0, lost_timeout_ms=2000,
                 session_idle_s=120, trajectory_sample_ms=500,
                 max_trajectory_points=20, max_tracks_per_message=50,
                 tick_interval_s=0.5, monotonic=None, clock=None, log=None, trace=None):
        if not device_id:
            raise ValueError("device_id 不能为空")
        if not callable(publish):
            raise ValueError("publish 必须为可调用的发送出口")
        self.device_id = str(device_id)
        self._publish_fn = publish
        # 参数在配置层已钳制，这里再钳一次（直接构造聚合器时也安全）
        self.window_s = clamp(float(window_s), *TRACK_WINDOW_S_RANGE)
        self.lost_timeout_s = clamp(int(lost_timeout_ms),
                                    *TRACK_LOST_TIMEOUT_MS_RANGE) / 1000.0
        self.session_idle_s = clamp(int(session_idle_s), *TRACK_SESSION_IDLE_S_RANGE)
        self.trajectory_sample_ms = clamp(int(trajectory_sample_ms),
                                          *TRACK_TRAJECTORY_SAMPLE_MS_RANGE)
        self.max_trajectory_points = clamp(int(max_trajectory_points),
                                           *TRACK_MAX_TRAJECTORY_POINTS_RANGE)
        self.max_tracks = clamp(int(max_tracks_per_message),
                                *TRACK_MAX_TRACKS_PER_MESSAGE_RANGE)
        self.tick_interval_s = clamp(float(tick_interval_s), *TRACK_TICK_INTERVAL_S_RANGE)
        self._monotonic = monotonic or time.monotonic
        self._clock = clock or (lambda: int(time.time() * 1000))
        self.log = log or _noop
        self.trace = trace or _noop

        self._lock = threading.Lock()
        # 窗口由独立线程驱动（与 TelemetryEngine 同构）：PUBACK 等待最长 3s/条，
        # 绝不能在 ROS 单线程执行器上同步阻塞（否则订阅/服务全部停摆）。
        # 事件同样先入队，由该线程串行处理（session_end 的结束序列也要等 PUBACK）。
        self._events = queue.Queue(maxsize=TRACK_EVENT_QUEUE_MAX)
        self._stop_event = threading.Event()
        self._thread = None
        self._state = _IDLE
        self._armed_area = None
        self._session = None
        self._session_seq = 0
        self._warn_last = {}
        self._counters = {
            "events_ok": 0,
            "events_bad_json": 0,
            "events_unknown": 0,
            "events_error": 0,
            "samples_rejected": 0,
            "episodes_total": 0,
            "windows_sent": 0,
            "messages_sent": 0,
            "messages_failed": 0,
            "send_errors": 0,
            "out_of_order": 0,
            "lost_timeouts": 0,
            "pending_dropped": 0,
            "events_dropped": 0,
        }

    # ---- 生命周期 ----

    def start(self):
        """启动独立窗口线程（幂等）；不依赖 ROS 执行器。"""
        if self._thread is not None and self._thread.is_alive():
            return
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._run, name="ppdv-comm-track",
                                        daemon=True)
        self._thread.start()

    def stop(self):
        """停止窗口线程；等待当前 tick 结束（发送最长 3s/条，故 join 3s）。"""
        self._stop_event.set()
        try:
            self._events.put_nowait(None)     # 唤醒队列等待
        except queue.Full:
            pass
        thread = self._thread
        if thread is not None:
            thread.join(timeout=3)
            if thread.is_alive():
                self._warn("thread_stop", "track 窗口线程仍在等待发送，超时未退出")
            self._thread = None

    def _run(self):
        """单线程：串行消费事件队列，并按 tick_interval_s 驱动窗口。

        订阅回调只调用 post() 入队；事件处理、窗口聚合与 PUBACK 等待都在本线程，
        ROS 执行器不再被网络 IO 阻塞。
        """
        interval = self.tick_interval_s
        next_tick = time.monotonic() + interval
        while not self._stop_event.is_set():
            timeout = next_tick - time.monotonic()
            try:
                raw = self._events.get(timeout=timeout) if timeout > 0 else None
            except queue.Empty:
                raw = None
            if self._stop_event.is_set():
                break
            if raw is not None:
                self.handle_event(raw)
            if time.monotonic() >= next_tick:
                self.tick()
                next_tick = time.monotonic() + interval

    # ---- 对外入口 ----

    def post(self, raw):
        """事件入队（ROS 订阅回调入口）：不做解析、聚合或网络等待。"""
        try:
            self._events.put_nowait(raw)
        except queue.Full:
            with self._lock:
                self._counters["events_dropped"] += 1
            self._warn("event_queue_full", "跟踪事件队列已满，丢弃最新事件")

    def handle_event(self, raw):
        """处理一条内部跟踪事件（JSON 文本或已解析 dict）；任何输入都不抛异常。"""
        try:
            event = raw if isinstance(raw, dict) else json.loads(raw)
        except (TypeError, ValueError):
            self._counters["events_bad_json"] += 1
            self._warn("bad_json", "跟踪事件 JSON 解析失败，丢弃")
            return
        if not isinstance(event, dict):
            self._counters["events_bad_json"] += 1
            self._warn("bad_json", "跟踪事件非 JSON 对象，丢弃")
            return
        name = event.get("event")
        name = name.strip().lower() if isinstance(name, str) else ""
        try:
            if name == "session_start":
                self._on_session_start(event)
            elif name == "detection":
                self._on_detection(event)
            elif name == "session_end":
                self._on_session_end(event)
            else:
                self._counters["events_unknown"] += 1
                self._warn("unknown_event", f"未知跟踪事件 event={event.get('event')!r}，丢弃")
                return
        except Exception as e:   # 单条事件异常不外溢（不得拖垮节点）
            self._counters["events_error"] += 1
            if name == "detection":
                self._counters["samples_rejected"] += 1
            self._warn("event_error", f"跟踪事件处理异常（整条事件已丢弃）: {e}")
            return
        self._counters["events_ok"] += 1

    def tick(self):
        """窗口线程入口：关闭超时出现 → 空闲结束会话 → 到点发送窗口。"""
        try:
            now_mono = self._monotonic()
            finish = None
            window = None
            with self._lock:
                session = self._session
                if session is None:
                    return
                self._expire_lost_locked(session, now_mono)
                if (self.session_idle_s > 0
                        and now_mono - session.last_detection_mono >= self.session_idle_s):
                    finish = self._finish_session_locked()
                elif now_mono - session.last_window_mono >= self.window_s:
                    messages = self._collect_window_locked(session, now_mono)
                    if messages:
                        window = (session, messages)
            if finish is not None:
                self._run_end_sequence(finish[0], finish[1], finish[2], "idle")
            elif window is not None:
                self._send_messages(window[0], window[1])
        except Exception as e:
            self._warn("tick_error", f"track tick 异常: {e}")

    def shutdown(self):
        """节点关停：先停窗口线程，再尽力结束当前会话（离线时结束序列进缓存）。"""
        self.stop()
        try:
            with self._lock:
                if self._session is None:
                    return
                batch = self._finish_session_locked()
            self._run_end_sequence(*batch, reason="shutdown")
        except Exception as e:
            self._warn("shutdown_error", f"track 关停结束会话异常: {e}")

    def stats(self):
        with self._lock:
            return dict(self._counters)

    # ---- 事件处理 ----

    def _on_session_start(self, event):
        area_id = event.get("areaId")
        if _is_number(area_id) and int(area_id) > 0:
            area_id = int(area_id)
        else:
            area_id = None
        with self._lock:
            if self._state == _ARMED:
                self._armed_area = area_id      # 幂等：最后一次 areaId 覆盖
                return
            if self._session is not None:
                # 契约（待感知端落地时确认）：会话进行中收到 session_start 视为重复，
                # 只记 WARN 且不切换 areaId；感知端必须保证 start/end 成对，否则
                # 下一次巡检的 areaId 会被丢弃、出现记到上一个区域。若改为
                # "先结束旧会话再开新会话"，须同步 README 契约与单测。
                self._warn("start_in_session", "会话已开始，忽略重复的 session_start（areaId 不生效）")
                return
            self._armed_area = area_id
            self._state = _ARMED

    def _on_session_end(self, event):
        reason = event.get("reason")
        reason = str(reason) if reason is not None else "event"
        with self._lock:
            if self._session is None:
                if self._state == _ARMED:
                    # 仅 ARMED（从未产生合法 detection）：只清理待用状态，不发任何报文
                    self._armed_area = None
                    self._state = _IDLE
                    self.trace("[track] session_end 清理待用会话（无出现，未发报文）")
                else:
                    self.trace("[track] 无会话时的 session_end，忽略")
                return
            batch = self._finish_session_locked()
        self._run_end_sequence(*batch, reason=reason)

    def _on_detection(self, event):
        now_mono = self._monotonic()
        with self._lock:
            sample, reason = self._parse_sample(event)
            if sample is None:
                self._counters["samples_rejected"] += 1
                key = "reject_" + reason
                self._counters[key] = self._counters.get(key, 0) + 1
                if self._session is not None:
                    self._session.rejected += 1
                self._warn("sample_" + reason, f"跟踪样本丢弃（{reason}）")
                return
            if self._session is None:
                self._start_session_locked(now_mono)
            session = self._session
            session.last_detection_mono = now_mono
            episode = session.active.get(sample.track_id)
            if episode is None:
                episode = self._new_episode_locked(session, sample, now_mono)
                session.active[sample.track_id] = episode
                self._counters["episodes_total"] += 1
                session.episodes += 1
            elif sample.frame_ts < episode.last_seen_ts:
                self._counters["out_of_order"] += 1
                self._warn("out_of_order",
                           f"乱序样本 track={sample.track_id} frameTs={sample.frame_ts} "
                           f"< lastSeenTs={episode.last_seen_ts}")
                if sample.end_state:
                    self._end_episode_locked(session, episode)
                return
            else:
                episode.add_sample(sample, now_mono, self.trajectory_sample_ms,
                                   self.max_trajectory_points)
            if sample.end_state:
                self._end_episode_locked(session, episode)

    # ---- 校验 ----

    def _parse_sample(self, event):
        """detection 校验 → (_Sample, None)；非法 → (None, 原因)。"""
        track_id = event.get("trackId")
        if not isinstance(track_id, str) or not 1 <= len(track_id.strip()) <= 64:
            return None, "trackId"
        track_id = track_id.strip()

        class_key = event.get("class")
        if not isinstance(class_key, str):
            return None, "class"
        class_key = class_key.strip().lower()
        if class_key in RETIRED_CLASSES:
            return None, "retired_class"
        if class_key not in TRACK_CLASSES:
            return None, "unknown_class"

        confidence = event.get("confidence")
        if not _is_number(confidence) or confidence < 0 or confidence > 1:
            return None, "confidence"

        frame_ts = event.get("frameTs")
        if not _is_int(frame_ts) or frame_ts <= 0:
            return None, "frameTs"
        if abs(frame_ts - self._clock()) > _MAX_TS_SKEW_MS:
            return None, "frameTs"

        box = self._parse_box(event)
        return _Sample(track_id, class_key, float(confidence), frame_ts, box,
                       self._parse_positive(event.get("imageWidth")),
                       self._parse_positive(event.get("imageHeight")),
                       self._parse_end_state(event.get("state"))), None

    @staticmethod
    def _parse_box(event):
        """bbox 四字段齐全且非负才有效（否则本样本不更新 bbox、不追加轨迹点）。"""
        values = (event.get("boxX"), event.get("boxY"),
                  event.get("boxW"), event.get("boxH"))
        for value in values:
            if not _is_number(value) or value < 0:
                return None
        return tuple(int(round(value)) for value in values)

    @staticmethod
    def _parse_positive(value):
        if not _is_number(value) or value <= 0:
            return None
        value = int(round(value))
        return value if value > 0 else None

    @staticmethod
    def _parse_end_state(value):
        """LOST/ENDED 表示本次出现结束；ACTIVE/REAPPEAR 及未知值都不结束。"""
        if not isinstance(value, str):
            return False
        return value.strip().upper() in ("LOST", "ENDED")

    # ---- 会话与出现状态机（调用方持锁） ----

    def _start_session_locked(self, now_mono):
        """首条被接受的 detection 才创建会话（避免 0 出现的空会话）。"""
        self._session_seq += 1
        now_ms = self._clock()
        suffix = f"-{now_ms}-{self._session_seq}"
        device = self.device_id[:max(8, 64 - 3 - len(suffix))]
        # device 已按 "64 - 3(TS-) - suffix" 切片，session_id 恒 ≤64；
        # 不要再从尾部截断（那会切掉 "TS-" 前缀，破坏 TS-{deviceId}- 契约）。
        session_id = "TS-" + device + suffix
        session = _Session(session_id, self._armed_area, self._session_seq,
                           now_ms, now_mono)
        self._session = session
        self._state = _ACTIVE      # 会话期：ACTIVE
        self._armed_area = None
        self.log(f"[track] 会话开始 session={session_id} 设备={self.device_id} "
                 f"areaId={'-' if session.area_id is None else session.area_id}")
        return session

    def _new_episode_locked(self, session, sample, now_mono):
        session.episode_seq += 1
        seq = session.episode_seq
        episode_no = session.episode_no.get(sample.track_id, -1) + 1
        session.episode_no[sample.track_id] = episode_no
        episode = _Episode(sample.track_id,
                           f"EP-{session.epoch_ms}-{session.seq}-{seq}",
                           episode_no, seq, sample, now_mono)
        episode.add_sample(sample, now_mono, self.trajectory_sample_ms,
                           self.max_trajectory_points)
        return episode

    def _end_episode_locked(self, session, episode):
        """本次出现结束：lastSeenTs 定格，进待发送已结束列表（不结束会话）。"""
        if episode.state == ENDED:
            return
        episode.state = ENDED
        session.active.pop(episode.track_id, None)
        session.pending.append(episode)
        overflow = len(session.pending) - MAX_PENDING_EPISODES
        if overflow > 0:
            # 离线抖动时 pending 无上界会让每窗全量重发/缓存重写退化为 O(N²)；
            # 丢最旧（云端可由后续窗口/下次出现补足）。
            del session.pending[:overflow]
            self._counters["pending_dropped"] += overflow
            self._warn("pending_full",
                       f"待确认出现超过 {MAX_PENDING_EPISODES} 条，丢弃最旧 {overflow} 条")

    def _expire_lost_locked(self, session, now_mono):
        """丢失超时：按单调时钟判定（不用相机 frameTs 与壁钟比较）。"""
        for episode in list(session.active.values()):
            if now_mono - episode.last_seen_mono >= self.lost_timeout_s:
                self._end_episode_locked(session, episode)
                self._counters["lost_timeouts"] += 1

    def _finish_session_locked(self):
        """结束会话：ACTIVE 全置 ENDED → 数据分片 + 空结束报文 → 清空状态。"""
        session = self._session
        for episode in list(session.active.values()):
            self._end_episode_locked(session, episode)
        window_index = session.window_index
        messages = self._chunk_locked(session, self._gather_locked(session), window_index)
        end_message = _Message(self._end_payload_locked(session, window_index), [], session)
        self._session = None
        self._state = _IDLE
        return session, messages, end_message

    # ---- 报文组装 ----

    def _collect_window_locked(self, session, now_mono):
        """常规窗口：无内容返回 None（空窗口绝不发送——云端会消费并刷新 0 出现会话）。

        windowIndex 只用于本地诊断/乱序排查：云端 TrackIngestService 不消费该字段，
        乱序与重复由 msgId Redis 去重 + lastSeenTs 单调守卫 + sessionEnd 幂等兜底。
        """
        episodes = self._gather_locked(session)
        if not episodes:
            return None
        messages = self._chunk_locked(session, episodes, session.window_index)
        if not messages:      # 防御：全部 track 自检失败时不发空窗口
            return None
        session.window_index += 1
        session.windows += 1
        session.last_window_mono = now_mono
        self._counters["windows_sent"] += 1
        return messages

    @staticmethod
    def _gather_locked(session):
        """窗口内容 = 所有 ACTIVE 出现 + 所有待发送的已结束出现，按 episodeSeq 升序。"""
        episodes = list(session.active.values()) + list(session.pending)
        episodes.sort(key=lambda item: item.episode_seq)
        return episodes

    def _chunk_locked(self, session, episodes, window_index):
        """按 track_max_tracks_per_message 分片；同窗所有分片共用同一个 windowIndex。"""
        messages = []
        size = self.max_tracks
        for start in range(0, len(episodes), size):
            chunk = episodes[start:start + size]
            ended = [episode for episode in chunk if episode.state == ENDED]
            data = self._data_payload_locked(session, chunk, window_index, False)
            if not data["tracks"]:    # 防御：该片全部 track 自检失败时整片不发
                continue
            messages.append(_Message(data, ended, session))
            self.trace(f"[track] session={session.session_id} window={window_index} "
                       f"tracks={len(data['tracks'])} 分片={len(messages)}")
        return messages

    def _data_payload_locked(self, session, chunk, window_index, session_end):
        tracks = []
        for episode in chunk:
            payload = episode.to_payload(self.max_trajectory_points)
            if not _track_payload_ok(payload):
                self._warn("bad_track", f"track 字段非法（{episode.episode_id}），不发送该条")
                continue
            tracks.append(payload)
        data = {"trackSessionId": session.session_id}
        if session.area_id is not None:
            data["areaId"] = session.area_id
        data["sessionEnd"] = session_end
        data["windowIndex"] = window_index
        data["tracks"] = tracks
        return data

    def _end_payload_locked(self, session, window_index):
        data = {"trackSessionId": session.session_id}
        if session.area_id is not None:
            data["areaId"] = session.area_id
        data["sessionEnd"] = True
        data["windowIndex"] = window_index
        data["tracks"] = []
        return data

    # ---- 发送 ----

    def _send_messages(self, session, messages, end_sequence=False):
        """按序发送数据分片；返回是否全部分片确认成功。"""
        all_ok = True
        for message in messages:
            if not end_sequence:
                with self._lock:
                    if self._session is not session:
                        self._warn("stale_window", "会话已结束，丢弃迟到的窗口批次")
                        return all_ok
            ok = self._publish(message.data, force_cache=False)
            session.messages += 1
            if ok:
                if message.ended:
                    with self._lock:
                        for episode in message.ended:
                            if episode in session.pending:
                                session.pending.remove(episode)
            else:
                all_ok = False
        return all_ok

    def _run_end_sequence(self, session, messages, end_message, reason):
        """结束序列：先发数据分片，全部分片确认后才在线发 tracks=[]+sessionEnd=true。"""
        all_ok = self._send_messages(session, messages, end_sequence=True)
        if end_message is not None:
            # 任一数据分片未确认 → 结束报文强制入缓存，保证缓存里"数据在前、结束在后"
            self._publish(end_message.data, force_cache=not all_ok)
            session.messages += 1
        duration = max(0.0, self._monotonic() - session.created_mono)
        self.log(f"[track] 会话结束 session={session.session_id} 原因={reason} "
                 f"出现={session.episodes} 报文={session.messages} 窗口={session.windows} "
                 f"拒绝样本={session.rejected} 持续={duration:.1f}s")

    def _publish(self, data, force_cache):
        try:
            ok = bool(self._publish_fn(data, force_cache=force_cache))
        except Exception as e:
            self._counters["send_errors"] += 1
            self._warn("send_error", f"track 发送出口异常: {e}")
            ok = False
        if ok:
            self._counters["messages_sent"] += 1
        else:
            self._counters["messages_failed"] += 1
        return ok

    # ---- 日志（同原因 10s 限流） ----

    def _warn(self, key, message):
        now = self._monotonic()
        last = self._warn_last.get(key)
        if last is not None and now - last < _WARN_INTERVAL_S:
            return
        self._warn_last[key] = now
        self.log(f"[track] WARN {message}")


def _track_payload_ok(payload) -> bool:
    """发送前自检（3.9）：字段越界属防御路径，正常流程不应触发。"""
    if not 1 <= len(payload["trackId"]) <= 64:
        return False
    if not 1 <= len(payload["episodeId"]) <= 64:
        return False
    if payload["class"] not in TRACK_CLASSES:
        return False
    confidence = payload["confidence"]
    if not _is_number(confidence) or confidence < 0 or confidence > 1:
        return False
    first_seen = payload["firstSeenTs"]
    last_seen = payload["lastSeenTs"]
    if not _is_int(first_seen) or not _is_int(last_seen):
        return False
    return 0 < first_seen <= last_seen
