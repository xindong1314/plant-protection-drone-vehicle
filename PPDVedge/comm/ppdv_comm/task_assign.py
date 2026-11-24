"""task/assign 内联任务解析（docs/protocol.md §6.2；v0.2 起内联，v0.4 未变更）。

云平台作业规划页把已录路线（首尾闭合）与地块边界（去闭环存储）以**内联**
[[lng,lat],...] 数组（经度在前）下发，route/field 缺一不可时为空数组；
另有 taskId/fieldId/areaId 与 params（如 speed/dosePerMu）。旧版的 routeUrl
文件引用已废弃，不再读取。

校验规则（本次范围：只解析、校验、打日志、回 ack，不映射导航执行）：
- route 必须存在且至少 2 点，每点为 [lng, lat] 数值对；
- field 允许为空数组；非空时逐点校验格式，但不强制最少点数/闭合
  （§11.1：路线录制语义保留首尾闭合、地块边界去闭环存储，是云平台组装
  语义，车端不做反校验）；
- taskId 缺失默认 T-{epoch 秒}。

本模块不依赖 rclpy / paho。
"""
import time

RETURN_CODE_OK = 0
RETURN_CODE_REJECTED = 1


class TaskAssignError(ValueError):
    """task/assign 结构校验失败（ack 回 code=1）。"""


def parse_point_list(raw, name: str, allow_empty: bool):
    """解析 [[lng,lat],...]；通过返回 [(lng, lat), ...]（float 元组），失败抛 TaskAssignError。"""
    if raw is None:
        if allow_empty:
            return []
        raise TaskAssignError(f"{name} 缺失")
    if not isinstance(raw, list):
        raise TaskAssignError(f"{name} 不是数组")
    if not raw and allow_empty:
        return []
    out = []
    for i, item in enumerate(raw):
        if not isinstance(item, (list, tuple)) or len(item) < 2:
            raise TaskAssignError(f"{name}[{i}] 不是 [lng,lat] 点")
        lng, lat = item[0], item[1]
        if isinstance(lng, bool) or isinstance(lat, bool) \
                or not isinstance(lng, (int, float)) or not isinstance(lat, (int, float)):
            raise TaskAssignError(f"{name}[{i}] 坐标不是数值")
        out.append((float(lng), float(lat)))
    return out


def parse_task_assign(data: dict) -> dict:
    """解析 task/assign 的 data；结构非法抛 TaskAssignError。

    返回 dict：taskId/fieldId/areaId/route/field/params。
    """
    if not isinstance(data, dict):
        raise TaskAssignError("data 不是对象")
    raw_task_id = data.get("taskId")
    task_id = str(raw_task_id) if raw_task_id else f"T-{int(time.time())}"
    raw_field_id = data.get("fieldId")
    field_id = str(raw_field_id) if raw_field_id else None
    area_id = data.get("areaId")
    params = data.get("params") if isinstance(data.get("params"), dict) else {}
    route = parse_point_list(data.get("route"), "route", allow_empty=False)
    if len(route) < 2:
        raise TaskAssignError(f"route 至少 2 点（当前 {len(route)}）")
    field = parse_point_list(data.get("field"), "field", allow_empty=True)
    return {
        "taskId": task_id,
        "fieldId": field_id,
        "areaId": area_id,
        "route": route,
        "field": field,
        "params": params,
    }
