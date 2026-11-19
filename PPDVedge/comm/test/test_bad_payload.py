"""坏包容错：非 JSON / 缺字段 / 类型错误不崩（传输层与下行层）。"""
import json

from conftest import FakeClock, RecordingSender, SyncExecutor
from ppdv_comm import protocol as P
from ppdv_comm.downlink import DownlinkHandler
from ppdv_comm.mqtt_link import EdgeMqttClient
from ppdv_comm.store import OfflineCache
from ppdv_comm.uplink import Uplink

DEVICE = "PPDV-0001"


def test_parse_envelope_rejects_garbage():
    assert P.parse_envelope('{"msgId": "bad-2", "data": {') is None      # 非法 JSON
    assert P.parse_envelope('[1, 2, 3]') is None                          # 非对象
    assert P.parse_envelope('not json at all') is None
    assert P.parse_envelope('') is None
    # 合法但字段类型错误仍是 dict（信封层不校验类型，下行层各自兜底）
    bad = {"msgId": "bad-3", "ts": "not-a-number", "type": "state", "data": "oops"}
    assert P.parse_envelope(P.dumps(bad)) == bad


def test_parse_envelope_missing_fields_still_dict():
    env = P.parse_envelope('{"not": "envelope"}')
    assert env == {"not": "envelope"}


class Capture:
    def __init__(self):
        self.got = []


def test_mqtt_client_bad_payload_no_crash():
    """on_message 收到坏包：记日志并忽略，不回调、不抛异常。"""
    from ppdv_comm.mqtt_link import MsgIdDeduper
    cap = Capture()

    class MinimalClient(EdgeMqttClient):
        # 绕开 paho 构造（单测无 broker/无网络）：只测回调逻辑
        def __init__(self):
            self.device_id = DEVICE
            self._log = lambda m: None
            self._trace = lambda m: None
            self._on_downlink = lambda k, e: cap.got.append((k, e))
            self.prefix = P.TOPIC_PREFIX + DEVICE + "/"
            self._deduper = MsgIdDeduper()

    c = MinimalClient()
    topic = P.topic_of(DEVICE, "cmd/req")
    for raw in (b'{"msgId": "x", "data": {',            # 非法 JSON
                b'[1,2,3]',                              # 非对象
                b'\xff\xfe\x00'):                        # 非法 UTF-8
        c._cb_message(None, None, type("M", (), {"topic": topic, "payload": raw})())
    assert cap.got == []
    # 合法 JSON 但缺字段：信封层不拦截，正常分发，由下行层兜底
    c._cb_message(None, None, type("M", (), {"topic": topic, "payload": b'{"not": "envelope"}'})())
    assert cap.got == [("cmd/req", {"not": "envelope"})]
    # 合法包正常分发
    good = P.dumps({"msgId": "g1", "ts": 1, "type": "cmd", "data": {"cmd": "STOP"}})
    c._cb_message(None, None, type("M", (), {"topic": topic, "payload": good.encode()})())
    assert cap.got[-1] == ("cmd/req", {"msgId": "g1", "ts": 1, "type": "cmd",
                                       "data": {"cmd": "STOP"}})


def test_mqtt_client_dedup_integration():
    """同 msgId 二次投递：忽略且不重复回调。"""
    from ppdv_comm.mqtt_link import MsgIdDeduper
    cap = Capture()

    class MinimalClient(EdgeMqttClient):
        def __init__(self):
            self.device_id = DEVICE
            self._log = lambda m: None
            self._trace = lambda m: None
            self._on_downlink = lambda k, e: cap.got.append((k, e))
            self.prefix = P.TOPIC_PREFIX + DEVICE + "/"
            self._deduper = MsgIdDeduper()

    c = MinimalClient()
    topic = P.topic_of(DEVICE, "cmd/req")
    good = P.dumps({"msgId": "dup-1", "ts": 1, "type": "cmd", "data": {"cmd": "START"}})
    raw = good.encode()
    for _ in range(3):  # 首次 + 两次重复投递
        c._cb_message(None, None, type("M", (), {"topic": topic, "payload": raw})())
    assert len(cap.got) == 1


def test_downlink_bad_payload_shapes_no_crash(tmp_path):
    sender = RecordingSender()
    uplink = Uplink(DEVICE, sender, OfflineCache(str(tmp_path / "cache"), DEVICE),
                    "1.2.0", clock=FakeClock())
    handler = DownlinkHandler(uplink, SyncExecutor())
    handler.actions = lambda cmd, args: (True, "ok")
    handler.spray = lambda action, data: (True, "ok")
    # data 缺失 / 非对象 / cmd 非字符串 / action 缺失 —— 全部不崩
    handler.handle("cmd/req", {"msgId": "b1", "ts": 1, "type": "cmd"})          # 无 data
    handler.handle("cmd/req", {"msgId": "b2", "ts": 1, "data": "oops"})         # data 非对象
    handler.handle("cmd/req", {"msgId": "b3", "ts": 1, "data": {"cmd": None}})  # cmd null
    handler.handle("cmd/req", {"msgId": "b4", "ts": 1, "data": {}})             # cmd 缺失
    handler.handle("spray/req", {"msgId": "b5", "ts": 1, "data": {}})           # action 缺失
    handler.handle("task/assign", {"msgId": "b6", "ts": 1, "data": None})       # data null
    handler.handle("config/set", {"msgId": "b7", "ts": 1})                      # 无 data
    acks = [json.loads(p) for p in sender.payloads_of("cmd/ack")]
    # b1/b2/b3/b4：cmd 缺失按"未知指令"交由 actions 层判定——本测试桩返回成功，
    # 故回 0（真实 sim 源会把未知 cmd 拒绝回 1，ack 语义由 actions 实现决定）；
    # b5 未知 action 回 1（未到 spray 执行层）；b6 解析失败回 1；b7 空配置回 0
    assert [a["data"]["code"] for a in acks] == [0, 0, 0, 0, 1, 1, 0]
