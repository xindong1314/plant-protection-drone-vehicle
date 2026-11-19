"""下行：ack refId 与码表、stale 拒绝、config 动态改频率、spray 映射。"""
import json

from conftest import FakeClock, RecordingSender, SyncExecutor
from ppdv_comm.downlink import DownlinkHandler
from ppdv_comm.store import OfflineCache
from ppdv_comm.uplink import Uplink

DEVICE = "PPDV-0001"


class HandlerHarness:
    def __init__(self, tmp_path, clock=None, stale=300):
        self.clock = clock or FakeClock()
        self.sender = RecordingSender()
        self.uplink = Uplink(DEVICE, self.sender,
                             OfflineCache(str(tmp_path / "cache"), DEVICE),
                             "1.2.0", clock=self.clock)
        self.actions_calls = []
        self.spray_calls = []
        self.task_calls = []
        self.handler = DownlinkHandler(
            self.uplink, SyncExecutor(), task_sink=self.task_calls.append,
            stale_cmd_max_age_s=stale, clock=self.clock)
        self.handler.actions = self._actions
        self.handler.spray = self._spray

    def _actions(self, cmd, args):
        self.actions_calls.append((cmd, args))
        return True, f"{cmd} done"

    def _spray(self, action, data):
        self.spray_calls.append((action, data))
        return True, "spray ok"

    def handle(self, kind, msg_id, data, ts=None):
        env = {"msgId": msg_id, "deviceId": "cloud", "ts": ts or self.clock.now_ms,
               "type": "x", "data": data}
        self.handler.handle(kind, env)

    def acks(self):
        return [json.loads(p) for p in self.sender.payloads_of("cmd/ack")]


def test_cmd_ack_refid_and_codes(tmp_path):
    h = HandlerHarness(tmp_path)
    h.handle("cmd/req", "c-1", {"cmd": "START"})
    h.handle("cmd/req", "c-2", {"cmd": "PAUSE"})
    acks = h.acks()
    assert [(a["data"]["refId"], a["data"]["code"]) for a in acks] == \
        [("c-1", 0), ("c-2", 0)]
    assert all(a["type"] == "ack" for a in acks)
    # ack 上行 QoS1
    assert all(q == 1 for (t, _p, q, _r) in h.sender.published if t.endswith("cmd/ack"))


def test_cmd_rejected_code_1(tmp_path):
    h = HandlerHarness(tmp_path)
    h.handler.actions = lambda cmd, args: (False, "当前模式不允许")
    h.handle("cmd/req", "c-3", {"cmd": "START"})
    acks = h.acks()
    assert acks[0]["data"]["code"] == 1
    assert acks[0]["data"]["msg"] == "当前模式不允许"


def test_stale_start_rejected_code_2(tmp_path):
    h = HandlerHarness(tmp_path, stale=300)
    h.handle("cmd/req", "c-old", {"cmd": "START"}, ts=h.clock.now_ms - 301_000)
    acks = h.acks()
    assert acks[0]["data"]["code"] == 2
    assert "已过期" in acks[0]["data"]["msg"]
    assert h.actions_calls == []  # 未执行


def test_fresh_start_executed(tmp_path):
    h = HandlerHarness(tmp_path, stale=300)
    h.handle("cmd/req", "c-new", {"cmd": "START"}, ts=h.clock.now_ms - 299_000)
    assert h.actions_calls == [("START", None)]
    assert h.acks()[0]["data"]["code"] == 0


def test_stale_boundary_exactly_max_age_is_executed(tmp_path):
    h = HandlerHarness(tmp_path, stale=300)
    h.handle("cmd/req", "c-edge", {"cmd": "RTL"}, ts=h.clock.now_ms - 300_000)
    assert h.actions_calls == [("RTL", None)]  # 300_000 > 300_000 不成立


def test_stale_applies_to_resume_and_rtl_only(tmp_path):
    h = HandlerHarness(tmp_path, stale=300)
    # PAUSE/STOP/SET_MODE 不判时效
    h.handle("cmd/req", "c1", {"cmd": "PAUSE"}, ts=h.clock.now_ms - 10_000_000)
    h.handle("cmd/req", "c2", {"cmd": "SET_MODE", "args": {"mode": "AUTO"}},
             ts=h.clock.now_ms - 10_000_000)
    assert [a["data"]["code"] for a in h.acks()] == [0, 0]
    assert len(h.actions_calls) == 2


def test_estop_no_stale_check_and_priority(tmp_path):
    h = HandlerHarness(tmp_path, stale=300)
    h.handle("cmd/req", "e1", {"cmd": "ESTOP"}, ts=h.clock.now_ms - 10_000_000)
    assert h.actions_calls == [("ESTOP", None)]  # 过期也立即执行
    assert h.acks()[0]["data"]["code"] == 0


def test_stale_ts_missing_or_bad_not_checked(tmp_path):
    h = HandlerHarness(tmp_path, stale=300)
    h.handle("cmd/req", "c1", {"cmd": "START"}, ts=0)
    h.handle("cmd/req", "c2", {"cmd": "START"}, ts="not-a-number")
    assert [a["data"]["code"] for a in h.acks()] == [0, 0]


def test_args_passthrough(tmp_path):
    h = HandlerHarness(tmp_path)
    h.handle("cmd/req", "c1", {"cmd": "SET_MODE", "args": {"mode": "AUTO"}})
    assert h.actions_calls == [("SET_MODE", {"mode": "AUTO"})]


def test_config_sets_rate_and_records_others(tmp_path):
    h = HandlerHarness(tmp_path)
    h.handle("config/set", "cfg1", {"teleRateHz": 5, "maxSpeed": 1.8, "geofence": "F-1001"})
    assert h.uplink.rate == 5.0
    acks = h.acks()
    assert acks[0]["data"]["code"] == 0
    assert "teleRateHz=5" in acks[0]["data"]["msg"]
    assert "maxSpeed=1.8" in acks[0]["data"]["msg"]   # 记录
    assert "geofence=F-1001" in acks[0]["data"]["msg"]


def test_config_bad_rate_skipped_but_acked(tmp_path):
    h = HandlerHarness(tmp_path)
    before = h.uplink.rate
    h.handle("config/set", "cfg2", {"teleRateHz": "abc"})
    assert h.uplink.rate == before
    assert h.acks()[0]["data"]["code"] == 0


def test_config_rate_clamped(tmp_path):
    h = HandlerHarness(tmp_path)
    h.handle("config/set", "cfg3", {"teleRateHz": 99})
    assert h.uplink.rate == 10.0


def test_spray_maps_to_source_with_rename(tmp_path):
    h = HandlerHarness(tmp_path)
    h.handle("spray/req", "s1", {"action": "start", "areaCode": "A1", "duration": 300})
    h.handle("spray/req", "s2", {"action": "stop"})
    h.handle("spray/req", "s3", {"action": "pause"})
    actions = [a for (a, _d) in h.spray_calls]
    assert actions == ["start", "stop"]  # pause 未到 spray 执行层
    acks = h.acks()
    assert [(a["data"]["refId"], a["data"]["code"]) for a in acks] == \
        [("s1", 0), ("s2", 0), ("s3", 1)]


def test_spray_failure_ack_1(tmp_path):
    h = HandlerHarness(tmp_path)
    h.handler.spray = lambda action, data: (False, "药箱已空，无法开始喷洒")
    h.handle("spray/req", "s1", {"action": "start", "areaCode": "A1"})
    assert h.acks()[0]["data"]["code"] == 1


def test_ota_logged_no_ack(tmp_path):
    h = HandlerHarness(tmp_path)
    h.handle("ota/notify", "o1", {"version": "1.3.0", "url": "https://x"})
    assert h.acks() == []


def test_every_downlink_acked_with_refid(tmp_path):
    h = HandlerHarness(tmp_path)
    h.handle("cmd/req", "m1", {"cmd": "STOP"})
    h.handle("task/assign", "m2", {"route": [[120.0, 30.0], [120.1, 30.1]]})
    h.handle("config/set", "m3", {})
    h.handle("spray/req", "m4", {"action": "stop"})
    acks = h.acks()
    assert [a["data"]["refId"] for a in acks] == ["m1", "m2", "m3", "m4"]
