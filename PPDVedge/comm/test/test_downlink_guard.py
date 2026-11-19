"""ESTOP 抢占门与点喷 duration 自动停喷（审核修正项）。"""
import json

from conftest import FakeClock, RecordingSender, SyncExecutor
from ppdv_comm.downlink import (DownlinkHandler, SerialExecutor, clamp_spray_duration)
from ppdv_comm.store import OfflineCache
from ppdv_comm.uplink import Uplink

DEVICE = "PPDV-0001"


class Harness:
    def __init__(self, tmp_path, executor=None):
        self.clock = FakeClock()
        self.sender = RecordingSender()
        self.uplink = Uplink(DEVICE, self.sender,
                             OfflineCache(str(tmp_path / "cache"), DEVICE),
                             "1.2.0", clock=self.clock)
        self.actions_calls = []
        self.spray_calls = []
        self.fired_timers = []      # (delay, fn) 未触发的假 timer
        handler = DownlinkHandler(
            self.uplink, executor or SyncExecutor(),
            stale_cmd_max_age_s=300, clock=self.clock,
            timer=self._fake_timer)
        handler.actions = self._actions
        handler.spray = self._spray
        self.handler = handler

    def _fake_timer(self, delay, fn):
        t = _FakeTimer(delay, fn)
        self.fired_timers.append(t)
        return t

    def _actions(self, cmd, args):
        self.actions_calls.append(cmd)
        return True, f"{cmd} done"

    def _spray(self, action, data):
        self.spray_calls.append(action)
        return True, "spray ok"

    def handle(self, kind, msg_id, data, ts=None):
        env = {"msgId": msg_id, "deviceId": "cloud", "ts": ts or self.clock.now_ms,
               "type": "x", "data": data}
        self.handler.handle(kind, env)

    def acks(self):
        return [json.loads(p) for p in self.sender.payloads_of("cmd/ack")]


class _FakeTimer:
    def __init__(self, delay, fn):
        self.delay = delay
        self.fn = fn
        self.cancelled = False

    def fire(self):
        if not self.cancelled:
            self.fn()

    def cancel(self):
        self.cancelled = True


def test_clamp_spray_duration():
    assert clamp_spray_duration(None) == 0          # 缺省=持续喷至 stop
    assert clamp_spray_duration(0) == 0
    assert clamp_spray_duration("abc") == 0
    assert clamp_spray_duration(-5) == 0
    assert clamp_spray_duration(0.5) == 1           # <1s 上取 1
    assert clamp_spray_duration(300) == 300
    assert clamp_spray_duration(9999) == 3600       # 协议上限 3600


def test_spray_start_with_duration_arms_auto_stop(tmp_path):
    h = Harness(tmp_path)
    h.handle("spray/req", "s1", {"action": "start", "areaCode": "A1", "duration": 30})
    assert h.spray_calls == ["start"]
    assert len(h.fired_timers) == 1
    timer = h.fired_timers[0]
    assert timer.delay == 30 and not timer.cancelled
    # 到点自动补发 stop
    timer.fire()
    assert h.spray_calls == ["start", "stop"]
    acks = h.acks()
    assert acks[0]["data"]["code"] == 0
    assert "30s 后自动停止" in acks[0]["data"]["msg"]


def test_spray_start_without_duration_no_timer(tmp_path):
    h = Harness(tmp_path)
    h.handle("spray/req", "s1", {"action": "start"})            # 缺省
    h.handle("spray/req", "s2", {"action": "start", "duration": "abc"})  # 非法
    assert h.spray_calls == ["start", "start"]
    assert h.fired_timers == []   # 持续喷洒至 stop，无自动停


def test_spray_stop_cancels_pending_timer(tmp_path):
    h = Harness(tmp_path)
    h.handle("spray/req", "s1", {"action": "start", "duration": 100})
    h.handle("spray/req", "s2", {"action": "stop"})
    assert h.fired_timers[0].cancelled
    h.fired_timers[0].fire()
    assert h.spray_calls == ["start", "stop"]   # stop 不重复


def test_spray_restart_replaces_timer(tmp_path):
    h = Harness(tmp_path)
    h.handle("spray/req", "s1", {"action": "start", "duration": 100})
    h.handle("spray/req", "s2", {"action": "start", "duration": 5})
    assert len(h.fired_timers) == 2
    assert h.fired_timers[0].cancelled          # 第一次的定时器被取消
    h.fired_timers[1].fire()
    assert h.spray_calls == ["start", "start", "stop"]


def test_estop_gate_rejects_first_queued_motion_cmd(tmp_path):
    """ESTOP 置门：紧随其后的第一条 START/RESUME/RTL（即排队旧指令）被拒绝
    code=1，且该条被拒即关门——此后云端可正常下发新指令
    （"ESTOP 无持续封锁"语义；串行队列里迟到的旧指令只有第一条）。"""
    h = Harness(tmp_path)
    h.handler.handle_estop("e1", {"cmd": "ESTOP"})
    assert h.actions_calls == ["ESTOP"]
    h.handle("cmd/req", "c1", {"cmd": "START"})
    h.handle("cmd/req", "c2", {"cmd": "RESUME"})
    codes = [(a["data"]["refId"], a["data"]["code"]) for a in h.acks()]
    assert codes == [("e1", 0), ("c1", 1), ("c2", 0)]
    assert h.actions_calls == ["ESTOP", "RESUME"]   # START 被拒，RESUME 正常执行


def test_estop_gate_allows_non_motion_cmds(tmp_path):
    h = Harness(tmp_path)
    h.handler.handle_estop("e1", {"cmd": "ESTOP"})
    h.handle("cmd/req", "c1", {"cmd": "PAUSE"})
    h.handle("cmd/req", "c2", {"cmd": "SET_MODE", "args": {"mode": "MANUAL"}})
    assert h.actions_calls == ["ESTOP", "PAUSE", "SET_MODE"]


def test_estop_gate_closed_by_rejected_first_cmd(tmp_path):
    """被拒的第一条运动指令即关门：之后再下发的 START 正常执行。"""
    h = Harness(tmp_path)
    h.handler.handle_estop("e1", {"cmd": "ESTOP"})
    h.handle("cmd/req", "c1", {"cmd": "START"})     # 门内拒绝（旧指令）
    assert h.actions_calls == ["ESTOP"]
    h.handle("cmd/req", "c2", {"cmd": "START"})     # 门已关，正常执行
    assert h.actions_calls == ["ESTOP", "START"]
    assert h.acks()[-1]["data"]["code"] == 0


def test_estop_gate_not_triggered_by_normal_flow(tmp_path):
    h = Harness(tmp_path)
    h.handle("cmd/req", "c1", {"cmd": "START"})
    h.handle("cmd/req", "c2", {"cmd": "STOP"})
    h.handle("cmd/req", "c3", {"cmd": "START"})
    assert h.actions_calls == ["START", "STOP", "START"]


def test_serial_executor_runs_in_order():
    """串行执行器：submit 的任务按提交顺序执行（防旧指令晚于 ESTOP 落地）。"""
    ex = SerialExecutor()
    order = []
    import threading
    done = threading.Event()

    def work(n):
        order.append(n)
        if n == 2:
            done.set()

    ex.submit(work, 0)
    ex.submit(work, 1)
    ex.submit(work, 2)
    assert done.wait(timeout=5)
    ex.shutdown()
    assert order == [0, 1, 2]
