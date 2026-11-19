"""数据源抽象：车辆快照 + 快照变化采集。

数据源可插拔（ROS2 参数 source:=sim|ros）：
- sim：轮询本地模拟器（端口 8090）的
  GET /api/state 组装快照，并把下行指令映射为模拟器 REST；
- ros：真车接口，订阅参数化 ROS2 话题（骨架，见 README 假定）。

本模块与 sim_rest.py 不 import rclpy（HTTP 用 urllib 标准库）；
ros_state.py 仅在 node 层 import rclpy，骨架方法保持纯逻辑可测。
"""
from dataclasses import dataclass, field, replace
from typing import Optional


@dataclass(frozen=True)
class VehicleSnapshot:
    """tele/state 数据（协议 §5.2）。taskId 云平台不消费，保留占位。

    frozen dataclass：work/progress 部分更新经 replace() 生成新对象，
    不就地改共享实例（审核修正：防字段撕裂/闪烁）。lat/lon 为 None 表示
    源暂无定位——引擎跳过该轮 state 上报，不回填 (0,0) 假数据
    （云侧只把"非数值"当缺失，数值 0.0 会被当真实坐标入库）。
    """
    lat: Optional[float] = None
    lon: Optional[float] = None
    alt: Optional[float] = None
    heading: Optional[float] = None
    speed: Optional[float] = None
    rtk_fix: Optional[str] = None
    mode: Optional[str] = None
    battery: Optional[int] = None
    task_id: Optional[str] = None
    # tele/work（协议 §5.3）
    spraying: bool = False
    flow: Optional[float] = None
    pressure: Optional[float] = None
    tank_level: Optional[int] = None
    sprayed_area: Optional[float] = None
    dosage: Optional[float] = None
    # task/progress（协议 §5.5）
    task_status: Optional[str] = None
    progress: Optional[int] = None
    covered_area: Optional[float] = None
    # zone 判定辅助（sim 源本地判定用；协议无此字段）
    zones: list = field(default_factory=list)

    def has_position(self) -> bool:
        return self.lat is not None and self.lon is not None

    def with_updates(self, **kwargs) -> "VehicleSnapshot":
        """部分字段更新（不可变语义，用于 ros 源 work/progress 合并）。"""
        return replace(self, **kwargs)


def locate_zone(lat: float, lon: float, zones: list) -> Optional[str]:
    """按包围盒判定当前区域。

    zones 元素为 {zoneCode, latMin, latMax, lonMin, lonMax}；均含边界值；
    不命中返回 None（不发 zone）。缺键区域跳过。
    """
    for z in zones or []:
        if not isinstance(z, dict):
            continue
        try:
            code = z.get("zoneCode")
            if not code:
                continue
            if (z["latMin"] <= lat <= z["latMax"]) and (z["lonMin"] <= lon <= z["lonMax"]):
                return str(code)
        except (KeyError, TypeError):
            continue
    return None
