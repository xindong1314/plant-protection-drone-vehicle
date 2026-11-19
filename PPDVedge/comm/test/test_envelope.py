"""信封：固定键序 {msgId, deviceId, ts, type, data} 与类型。"""
import json

from conftest import FakeClock
from ppdv_comm import protocol as P
from ppdv_comm.store import OfflineCache
from ppdv_comm.uplink import Uplink

DEVICE = "PPDV-0001"


def make_uplink(clock=None, sender=None, tmp_path=None):
    return Uplink(DEVICE, sender, OfflineCache(str(tmp_path / "cache"), DEVICE),
                  "1.2.0", clock=clock or FakeClock())


def test_envelope_key_order(tmp_path):
    up = make_uplink(FakeClock(1_721_030_400_000), tmp_path=tmp_path)
    env = up.envelope("state", {"lat": 30.1})
    assert list(env.keys()) == ["msgId", "deviceId", "ts", "type", "data"]


def test_envelope_types_and_values(tmp_path):
    up = make_uplink(FakeClock(1_721_030_400_123), tmp_path=tmp_path)
    env = up.envelope("state", {"lat": 30.1})
    assert isinstance(env["msgId"], str) and len(env["msgId"]) == 36  # UUID
    assert env["deviceId"] == DEVICE
    assert env["ts"] == 1_721_030_400_123 and isinstance(env["ts"], int)
    assert env["type"] == "state"
    assert env["data"] == {"lat": 30.1}


def test_envelope_json_serialization_keeps_key_order_and_unicode(tmp_path):
    up = make_uplink(FakeClock(1_721_030_400_000), tmp_path=tmp_path)
    env = up.envelope("status", {"online": True, "fw": "1.2.0"})
    text = P.dumps(env)
    keys = list(json.loads(text).keys())
    assert keys == ["msgId", "deviceId", "ts", "type", "data"]
    # 中文不转义（对齐云平台 Jackson UTF-8 行为）
    assert P.dumps({"msg": "药箱已空"}) == '{"msg": "药箱已空"}'.replace(
        '{"msg": ', '{"msg": ') or "药箱已空" in P.dumps({"msg": "药箱已空"})


def test_topic_prefix_and_kind(tmp_path):
    assert P.topic_of(DEVICE, "tele/state") == "ppdv/v1/PPDV-0001/tele/state"
    assert P.kind_of("ppdv/v1/PPDV-0001/cmd/req") == "cmd/req"
    assert P.kind_of("ppdv/v1/x/task/assign") == "task/assign"
    assert P.kind_of("bad") == ""
    assert P.SUB_KINDS == ("cmd/req", "task/assign", "config/set", "spray/req", "ota/notify")


def test_ts_uses_injected_clock_not_wall(tmp_path):
    up = make_uplink(FakeClock(999), tmp_path=tmp_path)
    captured = {}

    def fake_publish(topic, payload, qos=0, retain=False):
        captured["payload"] = payload
        return True

    up.sender = type("S", (), {"is_connected": lambda self: True,
                               "publish": staticmethod(fake_publish)})()
    up.publish_state({"lat": 1.0})
    assert json.loads(captured["payload"])["ts"] == 999
