"""遥测引擎：按 uplink 当前速率自调度，采集数据源快照并完成上行。

轮次顺序：采集 → 补传优先 → state → work 条件发送 → zone 变化 → alarm →
progress 去重键，有两处审核修正：
- 补传先于快照检查：MQTT 在线但源暂无快照（sim REST 超时/ros 首帧未到）
  时也要补传缓存，不因快照早退而饿死；
- 定位门控：快照 lat/lon 缺失（None）时跳过 state/work 上报（云侧只把
  "非数值"当缺失，不能上报 (0,0) 假坐标），work/zone/alarm/progress 照常。

自调度：每轮执行完按 1000/uplink.getRate() 重新 schedule，频率变化下轮生效。
本模块不 import rclpy（时钟注入），便于单测。
"""
import threading
import time


def _noop(msg):
    pass


class ProgressKey:
    """进度去重键 (taskId, taskStatus, progress)。"""

    __slots__ = ("task_id", "status", "progress")

    def __init__(self, task_id, status, progress):
        self.task_id = task_id
        self.status = status
        self.progress = progress

    def __eq__(self, other):
        return (isinstance(other, ProgressKey)
                and self.task_id == other.task_id
                and self.status == other.status
                and self.progress == other.progress)

    def __hash__(self):
        return hash((self.task_id, self.status, self.progress))


class TelemetryEngine:

    def __init__(self, source, uplink, log=None, trace=None, clock=None):
        self.source = source
        self.uplink = uplink
        self.log = log or _noop
        self.trace = trace or _noop
        self._clock = clock or (lambda: time.perf_counter())   # 秒（单调）
        self._last_nanos = None
        self._tick_no = 0
        self._last_progress_key = None
        self._stop_event = threading.Event()
        self._thread = None

    # ---- 生命周期 ----

    def start(self):
        """启动遥测循环（幂等）。"""
        if self._thread and self._thread.is_alive():
            return
        self._stop_event.clear()
        self._last_nanos = self._clock()
        self._tick_no = 0
        self._last_progress_key = None
        self._thread = threading.Thread(target=self._run, name="ppdv-comm-telemetry",
                                        daemon=True)
        self._thread.start()

    def stop(self):
        self._stop_event.set()
        if self._thread:
            self._thread.join(timeout=3)

    # ---- 主循环 ----

    def _run(self):
        while not self._stop_event.is_set():
            period = 1.0 / max(self.uplink.rate, 0.1)
            self._stop_event.wait(period)
            if self._stop_event.is_set():
                break
            try:
                self.tick_round()
            except Exception as e:  # 单轮异常不终止循环
                self.log(f"遥测轮次执行异常，继续下一轮: {e}")

    def tick_round(self):
        """单轮主循环（可独立调用，便于单测与手动驱动）。"""
        now = self._clock()
        dt = min(now - self._last_nanos, 2.0) if self._last_nanos is not None else 0.0
        self._last_nanos = now
        self._tick_no += 1

        # 1) 断网恢复后先补传缓存（顺序：旧数据在前）；COMM_RECOVERED 由 flush 内部发。
        #    在快照检查之前：源暂无快照（REST 超时/ros 首帧未到）也不能饿死补传。
        if (not self.uplink.simulate_offline
                and self.uplink.sender.is_connected()
                and self.uplink.cache_size() > 0):
            self.uplink.flush()

        # 2) 采集快照（sim 源为 REST 轮询；ros 源取订阅回调的最新缓冲）
        snapshot = None
        try:
            snapshot = self.source.poll_snapshot()
        except Exception as e:
            self.log(f"[源] 状态采集失败: {e}")
        if snapshot is None:
            return

        # 3) 定位门控：lat/lon 缺失（None）时不上报 state（不造 (0,0) 假坐标）；
        #    work 同样依赖定位语境（云侧 work 无坐标列，但缺定位时通常静止/初始化中）
        if not snapshot.has_position():
            self.trace("[源] 快照无有效定位，本轮跳过 state/work 上报")
            snapshot = None
            self._drain_side_channels()
            return

        # 4) state（协议 §5.2；云平台不消费 rtkFix/taskId，保留字段）
        self.uplink.publish_state(self._state_data(snapshot))

        # 5) work（喷洒中或任务 RUNNING 才发）
        if snapshot.spraying or snapshot.task_status == "RUNNING":
            self.uplink.publish_work(self._work_data(snapshot))

        self._drain_side_channels(snapshot)

    def _drain_side_channels(self, snapshot=None):
        """zone/alarm/progress 与定位无关（或自带定位语境），逐项排空。"""
        # zone（变化时；sim 源本地包围盒判定，ros 源来自话题）
        zone = self.source.take_zone_change() if hasattr(self.source, "take_zone_change") else None
        if zone:
            self.uplink.publish_zone(zone)

        # alarm（源注入的告警，如真车低电量/低药量）
        take_alarms = getattr(self.source, "take_alarms", None)
        if take_alarms:
            for code, level, msg, lat, lon in take_alarms():
                self.uplink.publish_alarm(code, level, msg, lat, lon)

        # progress（去重键 (taskId,status,progress) 变化且 taskId 非空才发）
        if snapshot is not None and snapshot.task_id:
            key = ProgressKey(snapshot.task_id, snapshot.task_status, snapshot.progress)
            if key != self._last_progress_key:
                self._last_progress_key = key
                self.uplink.publish_progress(self._progress_data(snapshot))

    # ---- 协议字段组装（键序对齐协议 §5 各示例） ----

    @staticmethod
    def _state_data(s) -> dict:
        data = {"lat": s.lat, "lon": s.lon}
        if s.alt is not None:
            data["alt"] = s.alt
        if s.heading is not None:
            data["heading"] = s.heading
        if s.speed is not None:
            data["speed"] = s.speed
        if s.rtk_fix is not None:
            data["rtkFix"] = s.rtk_fix
        if s.mode is not None:
            data["mode"] = s.mode
        if s.battery is not None:
            data["battery"] = s.battery
        data["taskId"] = s.task_id
        return data

    @staticmethod
    def _work_data(s) -> dict:
        data = {"spraying": bool(s.spraying)}
        if s.flow is not None:
            data["flow"] = s.flow
        if s.pressure is not None:
            data["pressure"] = s.pressure
        if s.tank_level is not None:
            data["tankLevel"] = s.tank_level
        if s.sprayed_area is not None:
            data["sprayedArea"] = s.sprayed_area
        if s.dosage is not None:
            data["dosage"] = s.dosage
        return data

    @staticmethod
    def _progress_data(s) -> dict:
        return {
            "taskId": s.task_id,
            "status": s.task_status,
            "progress": s.progress if s.progress is not None else 0,
            "coveredArea": s.covered_area if s.covered_area is not None else 0.0,
        }
