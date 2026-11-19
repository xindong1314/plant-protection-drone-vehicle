"""下行处理：cmd/req、task/assign、config/set、spray/req、ota/notify → cmd/ack 回执。

下行处理按 docs/protocol.md §6.2 实现（task/assign 内联 route/field，不读 routeUrl；
v0.4 未变更该节）。

协议 §7：每条下行指令必须回 cmd/ack，refId 关联原指令 msgId；码表 0 成功 / 1 拒绝 / 2 过期。
协议 §8：过期控制指令（START/RESUME/RTL）从严处理（ts 缺失/非数值不判过期）。

不依赖 rclpy：ack 走注入的 uplink，任务结果走注入的 task_sink（node 层发布
ROS2 话题），执行器（executor.submit）由调用方注入——回调线程不做长耗时操作。

ESTOP 抢占语义（审核修正）：下行处理经单线程串行执行器排队；ESTOP 跳过队列
立即执行，并在回调线程登记"急停窗口"——排队中尚未执行的 START/RESUME/RTL
在窗口期内被拒绝（回 code=1），防止旧指令在急停落地后把车重新启动。窗口在
ESTOP 执行完成后自动失效，此后云端可正常 START 恢复（无持续封锁）。

点喷 duration（审核修正）：start 带 duration（秒，协议限 1~3600，缺省/非法=
持续喷洒至 stop）时由本层计时到点自动补发 stop——模拟器 /api/spray 无时长
参数，云侧 spray/req 的 duration 是点喷唯一的自动停喷机制，必须在 comm 层兜底。
"""
import threading

from . import protocol as P
from . import task_assign as TA
from .config import clamp_rate

CMD_DESC = {
    "START": "启动作业",
    "PAUSE": "暂停作业",
    "RESUME": "恢复作业",
    "STOP": "停止（回 MANUAL）",
    "ESTOP": "急停（最高优先级）",
    "RTL": "返航",
    "SET_MODE": "切换模式",
}

STALE_CMDS = ("START", "RESUME", "RTL")

# ack 码表
ACK_OK = 0
ACK_REJECTED = 1
ACK_STALE = 2

SPRAY_DURATION_MIN, SPRAY_DURATION_MAX = 1, 3600


def _noop(msg):
    pass


def _as_long(o) -> int:
    """宽松取整数：Number 直接取值；字符串可解析则取值；否则 0（视为缺失、不做过期判定）。"""
    if isinstance(o, bool):
        return 0
    if isinstance(o, int):
        return o
    if isinstance(o, float):
        return int(o)
    if isinstance(o, str):
        try:
            return int(o.strip())
        except ValueError:
            return 0
    return 0


def _to_float(o):
    if isinstance(o, bool):
        return None
    if isinstance(o, (int, float)):
        return float(o)
    if isinstance(o, str):
        try:
            return float(o.strip())
        except ValueError:
            return None
    return None


def clamp_spray_duration(duration) -> int:
    """点喷时长收敛：缺省/非法 → 0（持续喷洒至 stop）；合法 → 1~3600 秒。
    小数秒向上取整（0.5s 也应触发一次停喷，而非按 0 丢弃）。"""
    if duration is None or isinstance(duration, bool):
        return 0
    f = _to_float(duration)
    if f is None:
        return 0
    n = -(-int(f * 1000) // 1000) if f > 0 else 0   # ceil 秒
    if n <= 0:
        return 0
    return max(SPRAY_DURATION_MIN, min(SPRAY_DURATION_MAX, n))


class SerialExecutor:
    """单线程串行执行器：下行指令按到达顺序处理，杜绝并发乱序。

    node 层默认注入本执行器（审核意见：多线程池下 STOP/SET_MODE 可能晚于
    ESTOP 落地）；单测/standalone 传 None 走同步直调。shutdown() 供节点
    优雅关停时排空队列。
    """

    def __init__(self, log=None):
        from concurrent.futures import ThreadPoolExecutor
        self._log = log or _noop
        self._pool = ThreadPoolExecutor(max_workers=1, thread_name_prefix="ppdv-comm-dl")

    def submit(self, fn, *args):
        return self._pool.submit(fn, *args)

    def shutdown(self, wait=True):
        self._pool.shutdown(wait=wait)


class DownlinkHandler:

    def __init__(self, uplink, executor, task_sink=None,
                 stale_cmd_max_age_s: int = 300, clock=None, log=None, trace=None,
                 timer=None):
        """executor 需实现 submit(fn, *args)（长耗时下行处理全部经它执行，
        不占 MQTT 回调线程；node 层注入 SerialExecutor 串行执行）；task_sink
        (parsed: dict) 为 task/assign 解析结果出口（node 层发布 ROS2 话题，
        纯本地模式可为 None）；clock 为 () -> epoch ms；timer(delay_s, fn)
        为可注入定时器（点喷 duration 到点自动 stop 用；缺省 threading.Timer）。"""
        self.uplink = uplink
        self.executor = executor
        self.task_sink = task_sink
        self.stale_cmd_max_age_s = max(0, int(stale_cmd_max_age_s))
        self._clock = clock or _default_now
        self._timer = timer or _default_timer
        self.log = log or _noop
        self.trace = trace or _noop
        # ESTOP 抢占窗口：ESTOP 执行前置位，执行完成后清零（START/RESUME/RTL
        # 在窗口内被拒绝，防止排队中的旧指令在急停后把车重新启动）
        self._estop_gate = 0
        self._estop_lock = threading.Lock()
        self._spray_stop_timer = None
        self._spray_lock = threading.Lock()

    def clock(self) -> int:
        return int(self._clock())

    # ---- 入口 ----

    def handle(self, kind: str, env: dict):
        """唯一入口（已过 512 去重）。重活全部提交执行器，本函数只分发。"""
        self.trace(f"[RX] {kind} {env}")
        msg_id = str(env.get("msgId") or "")
        data = env.get("data")
        if not isinstance(data, dict):
            data = {}
        try:
            if kind == "cmd/req":
                self._submit(self._cmd, msg_id, env.get("ts", 0), data)
            elif kind == "task/assign":
                self._submit(self._task, msg_id, data)
            elif kind == "config/set":
                self._submit(self._config, msg_id, data)
            elif kind == "spray/req":
                self._submit(self._spray, msg_id, data)
            elif kind == "ota/notify":
                self.log(f"[OTA] 收到升级通知: version={data.get('version')} "
                         f"url={data.get('url')}（本次忽略升级）")
            else:
                self.log(f"[下行] 未处理的下行主题: {kind}")
        except Exception as e:  # 分发异常不影响 MQTT 连接
            self.log(f"[下行] 处理异常 {kind}: {e}")

    def _submit(self, fn, *args):
        if self.executor is None:
            fn(*args)
        else:
            self.executor.submit(fn, *args)

    # ---- 各类下行 ----

    def _cmd(self, msg_id: str, ts, data: dict):
        cmd = str(data.get("cmd") or "").upper()
        self.log(f"[指令] 收到云端指令 {cmd}（{CMD_DESC.get(cmd, '未知指令')}）")
        if cmd in STALE_CMDS:
            # ESTOP 抢占窗口内的排队旧指令：拒绝执行并顺手关门（防急停被静默
            # 解除；本条被拒即代表"急停后到达的第一条运动指令"，此后云端可
            # 正常下发新指令恢复，无持续封锁）
            with self._estop_lock:
                in_gate = self._estop_gate > 0
                if in_gate:
                    self._estop_gate = 0
            if in_gate:
                self.log(f"[指令] 急停窗口内拒绝执行 {cmd}（窗口已关闭，重新下发即可执行）")
                self._ack(msg_id, ACK_REJECTED, f"急停抢占窗口内，拒绝执行 {cmd}")
                return
            now_ms = int(self.clock())
            ts_ms = _as_long(ts)
            if ts_ms > 0 and now_ms - ts_ms > self.stale_cmd_max_age_s * 1000:
                self.log(f"[指令] 指令已过期（>{self.stale_cmd_max_age_s}s），拒绝执行")
                self._ack(msg_id, ACK_STALE,
                          f"指令已过期（{self.stale_cmd_max_age_s}s），拒绝执行")
                return
        try:
            ok, msg = self.actions(cmd, data.get("args"))
        except Exception as e:
            ok, msg = False, f"执行异常: {e}"
        self.log(f"[指令] 执行{'成功' if ok else '被拒绝'}：{msg}（已回执 refId={msg_id}）")
        self._ack(msg_id, ACK_OK if ok else ACK_REJECTED, msg)

    def actions(self, cmd: str, args) -> tuple:
        """指令执行接口，由 node 层按数据源注入（sim 源 → REST，ros 源 → 骨架）。
        返回 (ok, msg)。ESTOP 的抢占窗口由本层维护，node 层只提供执行。"""
        raise NotImplementedError("node 层必须注入 actions")

    def handle_estop(self, msg_id: str, data: dict):
        """ESTOP 快速通道（node 层在 MQTT 回调线程直接调用，跳过执行器队列）：
        置抢占窗口（排队/后续到达的第一条 START/RESUME/RTL 将被拒绝并关门），
        然后立即执行。窗口在第一条运动指令被拒或正常执行后自动关闭。"""
        with self._estop_lock:
            self._estop_gate += 1
        self._cmd(msg_id, _default_now(), data)

    def _task(self, msg_id: str, data: dict):
        """task/assign：按 §6.2 解析内联 route/field，只解析、校验、打日志、回 ack，
        不映射到模拟器 REST（/api/task 只是推进当前任务的接口，不是任务下发）。"""
        try:
            parsed = TA.parse_task_assign(data)
        except TA.TaskAssignError as e:
            self.log(f"[任务] 解析失败：{e}")
            self._ack(msg_id, ACK_REJECTED, f"任务解析失败: {e}")
            return
        route, field = parsed["route"], parsed["field"]
        self.log(f"[任务] 收到云端任务 {parsed['taskId']}（fieldId={parsed['fieldId']}，"
                 f"areaId={parsed['areaId']}，route {len(route)} 点，field {len(field)} 点，"
                 f"params={parsed['params']}），已回执")
        if self.task_sink is not None:
            try:
                self.task_sink(parsed)
            except Exception as e:  # 话题发布失败不影响 ack
                self.log(f"[任务] 结果发布失败: {e}")
        self._ack(msg_id, ACK_OK, f"TASK {parsed['taskId']} ACCEPTED")

    def _config(self, msg_id: str, data: dict):
        applied = []
        hz = _to_float(data.get("teleRateHz"))
        if hz is not None:
            # 0/negative 也按 clamp 处理（收敛到 0.1），不再当"无效"忽略
            self.uplink.set_rate(hz)
            applied.append(f"teleRateHz={data['teleRateHz']}")
        elif data.get("teleRateHz") is not None:
            self.log(f"[配置] teleRateHz={data.get('teleRateHz')} 不是有效数值，忽略")
        ms = _to_float(data.get("maxSpeed"))
        if data.get("maxSpeed") and ms is not None:
            # 只记录、不执行（本节点无运动控制）
            applied.append(f"maxSpeed={ms}（记录，不执行）")
        if data.get("geofence"):
            applied.append(f"geofence={data.get('geofence')}（记录，不做围栏判定）")
        joined = ", ".join(applied) or "空配置"
        self.log(f"[配置] 收到云端参数下发：{joined}")
        self._ack(msg_id, ACK_OK, "CONFIG APPLIED: " + joined)

    def _spray(self, msg_id: str, data: dict):
        action = str(data.get("action") or "").lower()
        area_code = data.get("areaCode")
        duration = data.get("duration")
        if action == "start":
            self._cancel_spray_timer()
            self.log(f"[点喷] 收到云端点喷开始指令（区域 {area_code}，时长 {duration} 秒）")
        elif action == "stop":
            self._cancel_spray_timer()
            self.log("[点喷] 收到云端点喷停止指令")
        else:
            self.log(f"[点喷] 收到未知 action={action}，拒绝")
            self._ack(msg_id, ACK_REJECTED, f"未知 action={action}")
            return
        try:
            ok, msg = self.spray(action, data)
        except Exception as e:
            ok, msg = False, f"执行异常: {e}"
        if ok and action == "start":
            seconds = clamp_spray_duration(duration)
            if seconds > 0:
                # duration 是点喷唯一的自动停喷机制（模拟器 /api/spray 无时长参数，
                # 云侧只透传 payload），由本层计时到点自动补发 stop
                self._arm_spray_timer(seconds)
                msg = f"{msg}（{seconds}s 后自动停止）"
        self.log(f"[点喷] 执行{'成功' if ok else '被拒绝'}：{msg}（已回执 refId={msg_id}）")
        self._ack(msg_id, ACK_OK if ok else ACK_REJECTED, msg)

    def spray(self, action: str, data: dict) -> tuple:
        """点喷执行接口，由 node 层按数据源注入。返回 (ok, msg)。"""
        raise NotImplementedError("node 层必须注入 spray")

    def _arm_spray_timer(self, seconds: int):
        def auto_stop():
            with self._spray_lock:
                self._spray_stop_timer = None
            self.log(f"[点喷] 时长 {seconds}s 到点，自动停止喷洒")
            try:
                self.spray("stop", {})
            except Exception as e:
                self.log(f"[点喷] 自动停止失败: {e}")

        with self._spray_lock:
            self._spray_stop_timer = self._timer(seconds, auto_stop)

    def _cancel_spray_timer(self):
        with self._spray_lock:
            t = self._spray_stop_timer
            self._spray_stop_timer = None
        if t is not None:
            t.cancel()

    def shutdown(self):
        """节点关停时取消未触发的点喷定时器。"""
        self._cancel_spray_timer()

    def _ack(self, ref_id: str, code: int, msg: str):
        if self.uplink:
            self.uplink.publish_ack(ref_id, code, msg)


def _default_timer(delay_s: int, fn):
    t = threading.Timer(delay_s, fn)
    t.daemon = True
    t.start()
    return t


def _default_now() -> int:
    import time
    return int(time.time() * 1000)
