"""PPDV 车云通信协议常量与工具（对齐 PPDVapi protocol/Topics.java 与 docs/protocol.md v0.4）。

主题前缀统一 `ppdv/v1/{deviceId}/...`；信封固定键序 {msgId, deviceId, ts, type, data}。
本模块不得 import rclpy / paho（供传输层与单测复用）。
"""
import json
import uuid

TOPIC_PREFIX = "ppdv/v1/"

# 车端订阅的下行 kind（协议 §3，均 QoS1；恢复连接后必须全部恢复订阅）
SUB_KINDS = ("cmd/req", "task/assign", "config/set", "spray/req", "ota/notify")

# 上行 kind（协议 §5；tele/track 为 v0.3 收录、v0.4 身份模型修订的跟踪观测）
TRACK_KIND = "tele/track"
TRACK_TYPE = "track"
TRACK_STATE_ENDED = "ENDED"

# 在线 QoS（协议 §8）：高频状态/环境遥测 QoS0 可容忍丢帧，其余 QoS1
STATE_QOS = 0
RELIABLE_QOS = 1


def topic_of(device_id: str, kind: str) -> str:
    """按 deviceId 与 kind 拼完整主题，如 ppdv/v1/PPDV-0001/tele/state。"""
    return TOPIC_PREFIX + device_id + "/" + kind


def kind_of(topic: str) -> str:
    """主题的业务种类：第 4 段起 join("/")，如 ppdv/v1/x/cmd/req → cmd/req。"""
    if not topic:
        return ""
    parts = topic.split("/")
    if len(parts) <= 3:
        return ""
    return "/".join(parts[3:])


def make_msg_id() -> str:
    """上行 msgId：UUID（与云平台 Envelope.of 一致）。"""
    return str(uuid.uuid4())


def parse_envelope(payload: str):
    """解析 JSON 信封；失败或非 JSON 对象返回 None（调用方记日志忽略）。"""
    try:
        parsed = json.loads(payload)
    except (UnicodeDecodeError, ValueError):
        return None
    if not isinstance(parsed, dict):
        return None
    return parsed


def dumps(obj) -> str:
    """UTF-8 JSON 序列化，中文不转义（对齐云平台 Jackson 行为）。

    allow_nan=False：NaN/Infinity 不是合法 JSON，若漏到出口会生成云端
    Jackson 无法解析的裸字面量，导致整条报文被丢弃；这里统一拒绝。
    """
    return json.dumps(obj, ensure_ascii=False, allow_nan=False)
