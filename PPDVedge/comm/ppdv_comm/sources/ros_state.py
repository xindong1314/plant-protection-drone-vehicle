"""ros 数据源骨架：真车 ROS2 接口（本次不接真车，仅骨架 + 契约说明）。

假定的 ROS2 话题（名称均可用 comm.yaml 参数覆盖，见 config/comm.yaml 与 README）：

| 话题（参数默认值）                 | 消息类型                 | 用途 | 协议字段 |
|------------------------------------|--------------------------|------|----------|
| /ppdv/vehicle/state                | ppdv_msgs/VehicleState*  | 状态遥测 | lat/lon/alt/heading/speed/rtkFix/mode/battery/taskId |
| /ppdv/vehicle/work                 | ppdv_msgs/VehicleWork*   | 作业遥测 | spraying/flow/pressure/tankLevel/sprayedArea/dosage |
| /ppdv/vehicle/zone                 | std_msgs/String          | 区域进入 | zoneCode |
| /ppdv/vehicle/alarm                | ppdv_msgs/VehicleAlarm*  | 告警     | code/level/msg/lat/lon |
| /ppdv/vehicle/progress             | ppdv_msgs/TaskProgress*  | 任务进度 | taskId/status/progress/coveredArea |

* 自定义消息包 ppdv_msgs 本次不创建；接真车时二选一：
  (a) 新建 ppdv_msgs 定义上述消息；(b) 用 std_msgs/Float64MultiArray 等通用类型
  按约定槽位打包（不推荐，语义弱）。话题名改参数即可，不改代码。

线程模型（审核修正）：本源被 rclpy 订阅回调与遥测引擎线程并发访问，
状态读写全部持 self._lock；state 整体替换、work/progress 用不可变合并
（VehicleSnapshot.with_updates，replace 生成新对象），引擎读到的快照不会
撕裂，work/progress 到达次序也不覆盖已有定位。

cmd/spray 下行到真车的执行接口 apply_command/apply_spray 在真车控制模块
接入前返回拒绝（ack code=1），避免误回成功；node 层把它们注入
DownlinkHandler.actions/spray（与 sim 源一致，真车模式下指令有明确回执）。
"""
import threading

from .base import VehicleSnapshot


class RosStateSource:

    def __init__(self, node=None, topics=None, log=None):
        """node 为 rclpy Node（仅 comm_node 传入；单测传 None 仍可用状态缓冲部分）。"""
        self.node = node
        self.topics = topics or {}
        self.log = log or (lambda msg: None)
        self._latest = None
        self._lock = threading.Lock()
        self._zone_code = None
        self._zone_changed = False
        self._alarms = []

    # ---- 状态采集（由 rclpy 订阅回调写入；engine 每 tick 取走） ----

    def on_state(self, snap: VehicleSnapshot):
        """state 话题回调：把定位/模式/电量等 state 语境字段合并进最新快照
        （不整体替换——work/progress 先到时其字段不能被覆盖丢失；
        spraying 归 work 话题所有，state 合并不覆盖）。"""
        if not isinstance(snap, VehicleSnapshot):
            return
        with self._lock:
            base = self._latest or VehicleSnapshot()
            self._latest = base.with_updates(
                **{k: v for k, v in dict(
                    lat=snap.lat, lon=snap.lon,
                    alt=snap.alt, heading=snap.heading, speed=snap.speed,
                    rtk_fix=snap.rtk_fix, mode=snap.mode, battery=snap.battery,
                    task_id=snap.task_id,
                ).items() if v is not None})

    def on_work(self, work: dict):
        """work 话题回调：合并进最新快照（无 state 时暂存为占位快照，
        引擎在 has_position() 为 False 前不会上报，避免 (0,0) 假数据）。"""
        if not isinstance(work, dict):
            return
        with self._lock:
            base = self._latest or VehicleSnapshot()
            self._latest = base.with_updates(
                **{k: v for k, v in dict(
                    spraying=work.get("spraying", base.spraying),
                    flow=work.get("flow", base.flow),
                    pressure=work.get("pressure", base.pressure),
                    tank_level=work.get("tankLevel", base.tank_level),
                    sprayed_area=work.get("sprayedArea", base.sprayed_area),
                    dosage=work.get("dosage", base.dosage),
                ).items() if v is not None or k == "spraying"})

    def on_progress(self, progress: dict):
        """progress 话题回调：合并进最新快照（同 on_work 的防撕裂语义）。"""
        if not isinstance(progress, dict):
            return
        with self._lock:
            base = self._latest or VehicleSnapshot()
            self._latest = base.with_updates(
                **{k: v for k, v in dict(
                    task_id=progress.get("taskId", base.task_id),
                    task_status=progress.get("taskStatus", base.task_status),
                    progress=progress.get("progress", base.progress),
                    covered_area=progress.get("coveredArea", base.covered_area),
                ).items() if v is not None})

    def on_zone(self, zone_code: str):
        with self._lock:
            if zone_code and zone_code != self._zone_code:
                self._zone_code = zone_code
                self._zone_changed = True

    def on_alarm(self, code: str, level: str, msg: str, lat=None, lon=None):
        with self._lock:
            self._alarms.append((code, level, msg, lat, lon))

    def poll_snapshot(self):
        with self._lock:
            return self._latest

    def take_zone_change(self):
        with self._lock:
            changed = self._zone_changed
            self._zone_changed = False
            return self._zone_code if changed and self._zone_code else None

    def take_alarms(self):
        with self._lock:
            out, self._alarms = self._alarms, []
        return out

    # ---- 下行执行（骨架：未接真车控制前一律拒绝，ack code=1） ----

    def apply_command(self, cmd: str, args) -> tuple:
        return False, f"真车控制接口未接入，拒绝 {cmd}"

    def apply_spray(self, action: str, data: dict) -> tuple:
        return False, "真车喷洒接口未接入，拒绝点喷指令"
