"""MQTT 客户端封装（paho-mqtt 2.x）+ 下行 msgId 去重。

连接方式取 connect_async + loop_start（首连失败后台自动重试，
适配车载常驻节点；paho 自动重连 1~30s 退避）。

对应协议 §2 连接约定：ClientId edge-{deviceId}、一机一密、KeepAlive 30s、
CleanSession=false、LWT 遗嘱（retained 信封格式）+ 上线主动覆盖；
重连成功后恢复 5 类下行订阅（CleanSession=false 亦保留服务端订阅，此处双保险）。
对下行消息按 msgId 去重（QoS1 可能重复投递）：512 容量滑动窗口，
淘汰时机为窗口满后新 id 入队同时最旧 id 出队；
出窗 id 的重复报文视为新消息正常处理）。

本模块不 import rclpy；下行分发经注入的 on_downlink 回调。
"""
import json
import threading
from collections import deque

from . import protocol as P

DEDUP_CAPACITY = 512


class MsgIdDeduper:
    """下行 msgId 去重：512 容量滑动窗口（deque + set，O(1) 判重）。

    淘汰语义：窗口满 512 条后，新 id 入队同时最旧 id 出队；
    已在窗口内的 id 视为重复；出窗后的同 id 报文视为新消息正常处理。
    """

    def __init__(self, capacity: int = DEDUP_CAPACITY):
        self.capacity = capacity
        self._seen = deque()   # 无 maxlen：出窗一律显式 popleft
        self._seen_set = set()
        self._lock = threading.Lock()

    def is_duplicate_and_track(self, msg_id: str) -> bool:
        """登记 msgId 并返回是否重复。重复包不改变窗口状态。"""
        with self._lock:
            if msg_id in self._seen_set:
                return True
            self._seen.append(msg_id)
            self._seen_set.add(msg_id)
            while len(self._seen) > self.capacity:
                self._seen_set.discard(self._seen.popleft())
            return False

    def __contains__(self, msg_id: str) -> bool:
        with self._lock:
            return msg_id in self._seen_set

    def __len__(self) -> int:
        with self._lock:
            return len(self._seen)


def _noop(msg):
    pass


def _publish_ack_result(info, timeout_s: float) -> bool:
    """paho MQTTMessageInfo → 是否已确认；兼容 is_published 为方法或属性。"""
    if getattr(info, "rc", 0) != 0:      # mqtt.MQTT_ERR_SUCCESS
        return False
    # paho 2.1：wait_for_publish 超时只静默返回、不抛异常；随后用 is_published 判定。
    info.wait_for_publish(timeout=timeout_s)
    is_published = getattr(info, "is_published", False)
    if callable(is_published):
        is_published = is_published()
    return bool(is_published)


class EdgeMqttClient:
    """paho 封装：连接参数/LWT/重连/订阅恢复/下行去重分发。"""

    def __init__(self, device_id: str, secret: str, broker_host: str, broker_port: int,
                 on_downlink, keepalive: int = 30, tls: bool = False,
                 log=None, trace=None):
        import paho.mqtt.client as mqtt

        self.device_id = device_id
        self._log = log or _noop
        self._trace = trace or _noop
        self._on_downlink = on_downlink
        self.on_connected_cb = None   # 连接成功后回调（上线消息等），可随时替换
        self._connected = threading.Event()
        self._deduper = MsgIdDeduper()

        self.prefix = P.TOPIC_PREFIX + device_id + "/"
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2,
                                  client_id=f"edge-{device_id}", clean_session=False)
        self.client.username_pw_set(device_id, secret)
        # LWT：异常掉线时由 Broker 代发 retained 离线消息。信封格式含 msgId
        # （§2 要求 msgId 必填（v0.2 起），审核修正；遗嘱由 Broker 代发，自身不会
        # 经过本端去重窗口，携带 msgId 不影响下行去重）
        will = {"msgId": P.make_msg_id(), "deviceId": device_id, "ts": P_now_ms(),
                "type": "status", "data": {"online": False}}
        self.client.will_set(self.prefix + "status", json.dumps(will, ensure_ascii=False),
                             qos=1, retain=True)
        self.client.on_connect = self._cb_connect
        self.client.on_disconnect = self._cb_disconnect
        self.client.on_message = self._cb_message
        self.client.reconnect_delay_set(min_delay=1, max_delay=30)
        if tls:
            self.client.tls_set()
        self._broker_host, self._broker_port, self._keepalive = broker_host, broker_port, keepalive
        self.client.connect_async(broker_host, broker_port, keepalive=keepalive)

    def start(self):
        """启动网络线程（connect_async 后台重试直至成功）。"""
        self.client.loop_start()

    def stop(self):
        try:
            self.client.loop_stop()
            self.client.disconnect()
        except Exception as e:
            self._log(f"[连接] 停止异常: {e}")

    # ---- 对外 ----

    def is_connected(self) -> bool:
        return self._connected.is_set() and self.client.is_connected()

    def publish(self, topic: str, payload: str, qos: int = 0, retain: bool = False) -> bool:
        try:
            info = self.client.publish(topic, payload, qos=qos, retain=retain)
            # paho 返回 MQTTMessageInfo 对象（无 __eq__），须取其 .rc 与错误码比较
            return info.rc == 0  # mqtt.MQTT_ERR_SUCCESS
        except (RuntimeError, ValueError, OSError) as e:
            self._log(f"[发布异常] {topic}: {e}")
            return False

    def publish_wait_ack(self, topic: str, payload: str, qos: int = 1,
                         retain: bool = False, timeout_s: float = 3.0) -> bool:
        """发布并等待 PUBACK（有界超时）；仅 tele/track 使用。

        publish() 的 True 只代表入队，不代表 broker 已收；track 需要知道是否确认。
        True = QoS1 已确认；超时/失败 = False（调用方转缓存重试）。
        其他主题仍走 publish()，不把等待逻辑扩散到遥测。
        """
        try:
            info = self.client.publish(topic, payload, qos=qos, retain=retain)
            return _publish_ack_result(info, timeout_s)
        except (RuntimeError, ValueError, OSError) as e:
            self._log(f"[发布超时] {topic}: {e}")
            return False

    # ---- paho 回调（VERSION2 签名） ----

    def _cb_connect(self, client, userdata, flags, reason_code, properties):
        if reason_code.is_failure:
            self._log(f"[连接] 失败: {reason_code}（检查设备编号/密钥，EMQX 认证回调依赖后端已启动）")
            return
        self._connected.set()
        self._log(f"[连接] 已连接 EMQX（session_present={flags.session_present}）")
        for kind in P.SUB_KINDS:
            client.subscribe(self.prefix + kind, qos=1)
        if self.on_connected_cb:
            try:
                self.on_connected_cb()
            except Exception as e:  # 回调异常不影响连接
                self._log(f"[连接] on_connected 回调异常: {e}")

    def _cb_disconnect(self, client, userdata, disconnect_flags, reason_code, properties):
        self._connected.clear()
        self._log(f"[连接] 断开: {reason_code}（将自动重连）")

    def _cb_message(self, client, userdata, msg):
        payload_text = _decode(msg.payload)
        # 报文级 RX 日志（受 log_messages 开关控制）
        self._trace(f"[RX] {msg.topic} {payload_text[:300]}")
        env = P.parse_envelope(payload_text)
        if env is None:
            self._log(f"[RX 坏包] {msg.topic}: {_abbreviate(msg.payload)!r}")
            return
        kind = P.kind_of(msg.topic)
        msg_id = env.get("msgId")
        if msg_id and isinstance(msg_id, str):
            if self._deduper.is_duplicate_and_track(msg_id):
                self._log(f"[RX 去重] {kind} msgId={msg_id}（重复投递，忽略）")
                return
        self._on_downlink(kind, env)


def _decode(payload: bytes) -> str:
    if isinstance(payload, str):
        return payload
    try:
        return payload.decode("utf-8")
    except (UnicodeDecodeError, AttributeError):
        return "\x00invalid"  # 交由 parse_envelope 报坏包


def _abbreviate(payload) -> str:
    raw = payload if isinstance(payload, (bytes, bytearray)) else str(payload).encode()
    text = raw[:120]
    return text.decode("utf-8", errors="replace")


def P_now_ms() -> int:
    import time
    return int(time.time() * 1000)
