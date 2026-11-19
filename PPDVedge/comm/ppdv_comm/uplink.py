"""上行发送：统一信封、QoS 策略、断网缓存/补传。

在线 QoS（协议 §8）：tele/state 与 tele/env 等高频遥测用 QoS0（可容忍丢帧），
其余（status/work/zone/alarm/progress/ack）用 QoS1。
断网缓存（协议 §8）：离线时"遥测/告警"按时间落本地缓存、恢复后按序补传——
缓存策略与传输 QoS 解耦，QoS0 的 tele/state 离线同样进缓存；
retained 的 status 永不缓存（防陈旧 retained 覆盖新状态），tele/env 不缓存。

信封固定键序 {msgId, deviceId, ts, type, data}；ts=采集时间（epoch ms），
补传不改 ts。不依赖 rclpy：时钟（clock）、MQTT 发送端（sender）、日志（log）
全部注入。
"""
import threading
import time

from . import protocol as P
from .config import clamp_rate

# tele/track 报文体积上限（协议 §5.6：单条 ≤256KB）
TRACK_MAX_BYTES = 256 * 1024
# track 在线发布等 QoS1 PUBACK 的上限（硬编码 3s，不新增配置）
TRACK_ACK_TIMEOUT_S = 3.0
# flush 中 tele/track 条目的总等待预算：避免一次补传把遥测线程按 3s×N 拖住
FLUSH_TRACK_ACK_BUDGET_S = 3.0


def _noop(msg):
    pass


class Uplink:

    def __init__(self, device_id: str, sender, cache, fw: str, clock=None, log=None, trace=None):
        """sender 需实现 publish(topic, payload, qos, retain) -> bool 与 is_connected() -> bool；
        clock 为 () -> epoch ms（缺省 time.time()*1000）。"""
        self.device_id = device_id
        self.sender = sender
        self.cache = cache
        self.fw = fw
        self._clock = clock or (lambda: int(time.time() * 1000))
        self.log = log or _noop
        self.trace = trace or _noop
        self.rate = 1.0                # 上报频率，可被 config/set 动态修改
        self.simulate_offline = False  # 断网模拟开关（ROS2 参数/服务）
        self._lock = threading.Lock()
        # track 出站锁：track 发送与 flush 共用（统一出站锁）。锁序固定
        # "状态锁 → 本锁 → 缓存锁"，本锁内不得再取聚合器状态锁。
        self._track_lock = threading.Lock()

    # ---- 信封与发送 ----

    def envelope(self, type_: str, data: dict) -> dict:
        """固定键序信封：msgId(UUID)、deviceId、ts(毫秒)、type、data。"""
        return {
            "msgId": P.make_msg_id(),
            "deviceId": self.device_id,
            "ts": int(self._clock()),
            "type": type_,
            "data": data,
        }

    def send(self, kind: str, type_: str, data: dict, qos: int = P.STATE_QOS,
             retain: bool = False, cacheable: bool = True) -> bool:
        """按 kind 拼主题发送；离线时允许缓存的消息（遥测/告警，含 QoS0 state）进本地缓存。

        缓存写入与 flush 的 read→clear/重放 共用 self._lock（审核修正：
        防止补传中途并发 ack 的 cache.append 被 clear() 误删）。"""
        env = self.envelope(type_, data)
        try:
            payload = P.dumps(env)
        except ValueError as e:   # NaN/Infinity 不是合法 JSON，丢弃而不是发出坏包
            self.log(f"[丢弃] {kind}（报文含非有限数值）: {e}")
            return False
        topic = P.topic_of(self.device_id, kind)
        if self.simulate_offline or not self.sender.is_connected():
            if cacheable:
                with self._lock:
                    self.cache.append(topic, payload)
                self.log(f"[缓存] {kind}（离线，待补传）")
            else:
                self.trace(f"[丢弃] {kind}（离线且不缓存）")
            return False
        ok = self.sender.publish(topic, payload, qos=qos, retain=retain)
        if ok:
            self.trace(f"[TX] {topic} (qos{qos}{', retained' if retain else ''}) {payload}")
        elif cacheable:
            with self._lock:
                self.cache.append(topic, payload)
            self.log(f"[缓存] {kind}（发送失败，待补传）")
        return bool(ok)

    def publish_online(self) -> bool:
        return self.send("status", "status", {"online": True, "fw": self.fw},
                         qos=P.RELIABLE_QOS, retain=True, cacheable=False)

    def publish_offline(self) -> bool:
        return self.send("status", "status", {"online": False},
                         qos=P.RELIABLE_QOS, retain=True, cacheable=False)

    def publish_state(self, data: dict) -> bool:
        """QoS0 仅指在线传输可容忍丢帧；断网时按 §8 断网缓存，恢复后按序补传。"""
        return self.send("tele/state", "state", data, qos=P.STATE_QOS)

    def publish_work(self, data: dict) -> bool:
        return self.send("tele/work", "work", data, qos=P.RELIABLE_QOS)

    def publish_zone(self, zone_code: str) -> bool:
        return self.send("tele/zone", "zone", {"zoneCode": zone_code}, qos=P.RELIABLE_QOS)

    def publish_env(self, data: dict) -> bool:
        """tele/env 保留发送接口（不缓存）；本次不接数据源。
        QoS 对齐协议 §3（tele/env QoS1，审核修正：原用 QoS0 与协议表不一致）。"""
        return self.send("tele/env", "env", data, qos=P.RELIABLE_QOS, cacheable=False)

    def publish_alarm(self, code: str, level: str, msg: str, lat=None, lon=None) -> bool:
        data = {"code": code, "level": level, "msg": msg}
        if lat is not None and lon is not None:
            data["lat"], data["lon"] = lat, lon
        return self.send("event/alarm", "alarm", data, qos=P.RELIABLE_QOS)

    def publish_progress(self, data: dict) -> bool:
        return self.send("task/progress", "taskProgress", data, qos=P.RELIABLE_QOS)

    def publish_ack(self, ref_id: str, code: int, msg: str) -> bool:
        """协议 §7 指令回执：refId 关联下行 msgId。"""
        return self.send("cmd/ack", "ack",
                         {"refId": ref_id, "code": code, "msg": msg}, qos=P.RELIABLE_QOS)

    # ---- 目标跟踪观测 tele/track（协议 §5.6） ----

    def publish_track(self, data: dict, force_cache: bool = False) -> bool:
        """tele/track 专用发送：QoS1、不 retain、可缓存、需 PUBACK 确认。

        顺序（崩溃安全顺序）：同会话存在更小 windowIndex 的缓存旧窗口时，先持久化新报文，
        再原子清理旧窗口，最后在线发送；含 ENDED 的报文即便无旧条目也先持久化。
        在线发布在 3s 内等 PUBACK：确认成功则删掉新副本并返回 True；
        离线/强制缓存/超时失败返回 False（报文留在缓存待补传）。

        返回 True 仅代表"已确认送达"，入队成功不算（防止进程退出丢掉唯一副本）。
        """
        envelope = self.envelope(P.TRACK_TYPE, data)
        try:
            payload = P.dumps(envelope)
        except ValueError as e:   # NaN/Infinity 会在云端整条丢弃，这里直接拒绝
            self.log(f"[track] 报文含非有限数值，丢弃: {e}")
            return False
        topic = P.topic_of(self.device_id, P.TRACK_KIND)
        size = len(payload.encode("utf-8"))
        if size > TRACK_MAX_BYTES:
            self.log(f"[track] 报文 {size}B 超过 256KB，丢弃")
            return False
        session_id = str(data.get("trackSessionId") or "")
        window_index = data.get("windowIndex")
        session_end = data.get("sessionEnd") is True
        has_ended = any(isinstance(t, dict) and t.get("trackState") == P.TRACK_STATE_ENDED
                        for t in (data.get("tracks") or []))
        online = not self.simulate_offline and self.sender.is_connected()

        with self._track_lock:
            # 空缓存（在线常态）不做全量扫描；只有存在缓存条目时才检查旧窗口
            stale = (self.cache.has_entries()
                     and self.cache.has_stale_track_windows(topic, session_id,
                                                            window_index))
            persist = stale or has_ended or session_end
            if stale:
                # 先持久化新报文（追加成功），再原子清理同会话更小 windowIndex 的旧条目
                with self._lock:
                    if not self.cache.append(topic, payload):
                        self.log(f"[track] 缓存追加失败，跳过本次发送 session={session_id} "
                                 f"window={window_index}")
                        return False
                self.cache.prune_track_windows(topic, session_id, window_index)
            elif persist:
                # 含 ENDED / 会话结束的报文即便无旧条目也先落盘再发（保护最终状态）
                with self._lock:
                    if not self.cache.append(topic, payload):
                        self.log(f"[track] 缓存追加失败，跳过本次发送 session={session_id} "
                                 f"window={window_index}")
                        return False
            if force_cache or not online:
                if not persist:
                    with self._lock:
                        self.cache.append(topic, payload)
                self.log(f"[缓存] track session={session_id} window={window_index}"
                         f"{'（结束）' if session_end else ''}"
                         f"{'（强制，结束序列未全确认）' if force_cache else '（离线，待补传）'}")
                return False
            if self._publish_track_wait(topic, payload):
                if persist:
                    self.cache.remove_entry(topic, payload)   # 已确认：回收新副本
                self.trace(f"[TX] {topic} (qos1, {size}B) {payload[:300]}")
                return True
            if not persist:
                with self._lock:
                    self.cache.append(topic, payload)
            self.log(f"[缓存] track session={session_id} window={window_index}（未确认，待补传）")
            return False

    def _publish_track_wait(self, topic: str, payload: str,
                            timeout_s: float = TRACK_ACK_TIMEOUT_S) -> bool:
        """发布 track 报文并等待 QoS1 PUBACK；发送端不支持等待时退化为入队结果。"""
        try:
            waiter = getattr(self.sender, "publish_wait_ack", None)
            if waiter is None:
                return bool(self.sender.publish(topic, payload, qos=P.RELIABLE_QOS,
                                                retain=False))
            return bool(waiter(topic, payload, qos=P.RELIABLE_QOS, retain=False,
                               timeout_s=timeout_s))
        except (RuntimeError, ValueError, OSError) as e:
            self.log(f"[track] 发布/等待确认异常 {topic}: {e}")
            return False

    # ---- 断网恢复补传 ----

    def flush(self) -> int:
        """按序补传本地缓存（payload 保持原样，ts 为原采集时间）。

        中途失败则保留剩余缓存下轮再试；全部完成后发 COMM_RECOVERED 告警。
        返回成功补传条数。全程持 track 出站锁 + self._lock：与 track 发送共用
        同一把出站锁（防"新窗口已在线发出、旧窗口随后被补传"的倒序），
        并与 send 的缓存写入互斥（clear/重放不会误删补传期间新缓存的 ack/告警）。
        tele/track 条目同样等 QoS1 PUBACK 才算补传成功，未确认则保留该条及其后条目。
        """
        with self._track_lock:
            with self._lock:
                entries = self.cache.read_all()
                if not entries:
                    return 0
                sent = 0
                track_deadline = None
                for topic, payload in entries:
                    # 先按 topic 过滤再解析 payload：长时间离线后缓存可达数万行
                    if _is_track_topic(topic) and _is_track_payload(payload):
                        now = time.monotonic()
                        if track_deadline is None:
                            # 预算从第一条 track 条目开始算：普通遥测补传再多也不挤占
                            track_deadline = now + FLUSH_TRACK_ACK_BUDGET_S
                        remaining = track_deadline - now
                        if remaining <= 0:
                            break       # 预算用尽：保留该条及之后条目下轮再试
                        ok = self._publish_track_wait(
                            topic, payload,
                            timeout_s=min(TRACK_ACK_TIMEOUT_S, remaining))
                    else:
                        ok = self.sender.publish(topic, payload, qos=P.RELIABLE_QOS,
                                                 retain=False)
                    if ok:
                        sent += 1
                        self.trace(f"[补传] {topic} {payload[:120]}")
                    else:
                        break   # 中途失败则保留剩余缓存，下轮再试
                if sent == len(entries):
                    self.cache.clear()
                else:
                    # 仅保留未补传成功的剩余条目
                    self.cache.clear()
                    for topic, payload in entries[sent:]:
                        self.cache.append(topic, payload)
                count = sent
        if count:
            self.log(f"[补传] 完成 {count} 条（ts 为原采集时间）")
            self.publish_alarm("COMM_RECOVERED", "INFO", f"通信恢复，补传 {count} 条消息")
        return count

    # ---- 频率与离线开关 ----

    def set_rate(self, hz):
        """状态遥测频率调整，clamp 0.1~10 Hz（config/set 动态修改入口）。"""
        self.rate = clamp_rate(hz)
        self.log(f"[rate] 状态遥测频率调整为 {self.rate}Hz")

    def set_simulate_offline(self, enabled: bool):
        self.simulate_offline = bool(enabled)
        self.log(f"[net] 模拟离线{'开启' if self.simulate_offline else '关闭'}")

    def cache_size(self) -> int:
        return self.cache.size()


def _is_track_topic(topic: str) -> bool:
    """缓存条目主题是否为 tele/track（先做便宜的 topic 过滤，再解析 payload）。"""
    return P.kind_of(topic) == P.TRACK_KIND


def _is_track_payload(payload: str) -> bool:
    """缓存条目是否为 tele/track 报文（补传时需等 QoS1 PUBACK）。"""
    envelope = P.parse_envelope(payload)
    return envelope is not None and envelope.get("type") == P.TRACK_TYPE

