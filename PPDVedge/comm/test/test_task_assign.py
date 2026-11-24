"""task/assign 内联 route/field 解析（protocol.md §6.2）与下行集成。"""
import json

import pytest

from conftest import FakeClock, RecordingSender, SyncExecutor
from ppdv_comm import task_assign as TA
from ppdv_comm.downlink import DownlinkHandler
from ppdv_comm.store import OfflineCache
from ppdv_comm.uplink import Uplink

DEVICE = "PPDV-0001"


def parse_ok(data):
    return TA.parse_task_assign(data)


def test_parse_minimal_route_only():
    parsed = parse_ok({"route": [[120.654321, 30.123456], [120.655001, 30.123100]]})
    assert parsed["route"] == [(120.654321, 30.123456), (120.655001, 30.123100)]
    assert parsed["field"] == []
    assert parsed["taskId"].startswith("T-")      # 缺省默认
    assert parsed["fieldId"] is None and parsed["areaId"] is None
    assert parsed["params"] == {}


def test_parse_full_payload():
    parsed = parse_ok({
        "taskId": "T-20260715-01", "fieldId": "AREA-12", "areaId": 12,
        "route": [[120.654321, 30.123456], [120.655001, 30.123100]],
        "field": [[120.653800, 30.122100], [120.656500, 30.122300], [120.656100, 30.124600]],
        "params": {"speed": 1.5, "dosePerMu": 0.9},
    })
    assert parsed["taskId"] == "T-20260715-01"
    assert parsed["fieldId"] == "AREA-12"
    assert parsed["areaId"] == 12
    assert len(parsed["route"]) == 2 and len(parsed["field"]) == 3
    assert parsed["params"] == {"speed": 1.5, "dosePerMu": 0.9}


def test_route_lng_first_ordering_preserved():
    """经度在前：解析结果不交换 lng/lat。"""
    parsed = parse_ok({"route": [[1.0, 2.0], [3.0, 4.0]]})
    assert parsed["route"][0] == (1.0, 2.0)  # lng=1.0, lat=2.0


def test_route_at_least_2_points():
    with pytest.raises(TA.TaskAssignError):
        parse_ok({"route": []})
    with pytest.raises(TA.TaskAssignError):
        parse_ok({"route": [[120.0, 30.0]]})
    with pytest.raises(TA.TaskAssignError):
        parse_ok({"field": []})  # route 缺失


def test_field_empty_allowed():
    parsed = parse_ok({"route": [[1.0, 1.0], [2.0, 2.0]], "field": []})
    assert parsed["field"] == []
    parsed = parse_ok({"route": [[1.0, 1.0], [2.0, 2.0]]})  # field 缺失视同空
    assert parsed["field"] == []


def test_field_no_closure_or_min_points_check():
    """地块边界不强制闭合/最少点数（§11.1 去闭环存储是云平台组装语义）。"""
    parsed = parse_ok({"route": [[1.0, 1.0], [2.0, 2.0]],
                       "field": [[1.0, 1.0], [2.0, 2.0]]})  # 2 点未闭合
    assert len(parsed["field"]) == 2


def test_bad_point_shapes_rejected():
    with pytest.raises(TA.TaskAssignError):
        parse_ok({"route": [[120.0], [1.0]]})          # 单元素
    with pytest.raises(TA.TaskAssignError):
        parse_ok({"route": "120,30"})                  # 非数组
    with pytest.raises(TA.TaskAssignError):
        parse_ok({"route": [[120.0, "30"], [1.0, 1.0]]})  # 坐标非数值
    with pytest.raises(TA.TaskAssignError):
        parse_ok({"route": [[True, 1.0], [1.0, 1.0]]})    # bool 不算数值
    with pytest.raises(TA.TaskAssignError):
        parse_ok("not-a-dict")


# ---- 下行集成：解析失败 ack 1 / 成功 ack 0 + task_sink 发布 ----


def harness(tmp_path):
    sender = RecordingSender()
    uplink = Uplink(DEVICE, sender, OfflineCache(str(tmp_path / "cache"), DEVICE),
                    "1.2.0", clock=FakeClock())
    tasks = []
    handler = DownlinkHandler(uplink, SyncExecutor(), task_sink=tasks.append)
    handler.actions = lambda cmd, args: (True, "ok")
    handler.spray = lambda action, data: (True, "ok")
    return sender, tasks, handler


def handle(handler, msg_id, data):
    handler.handle("task/assign", {"msgId": msg_id, "ts": 1, "type": "task", "data": data})


def test_valid_task_acked_0_and_published(tmp_path):
    sender, tasks, handler = harness(tmp_path)
    handle(handler, "t1", {"taskId": "T-1", "fieldId": "AREA-12", "areaId": 12,
                           "route": [[120.0, 30.0], [120.1, 30.1], [120.2, 30.0]],
                           "field": [[120.0, 30.0], [120.1, 30.2], [120.2, 30.0]]})
    acks = [json.loads(p) for p in sender.payloads_of("cmd/ack")]
    assert acks[0]["data"]["code"] == 0
    assert acks[0]["data"]["refId"] == "t1"
    assert len(tasks) == 1
    assert tasks[0]["taskId"] == "T-1"
    assert tasks[0]["route"] == [(120.0, 30.0), (120.1, 30.1), (120.2, 30.0)]
    assert tasks[0]["field"] == [(120.0, 30.0), (120.1, 30.2), (120.2, 30.0)]


def test_invalid_task_acked_1_not_published(tmp_path):
    sender, tasks, handler = harness(tmp_path)
    handle(handler, "t2", {"route": [[120.0, 30.0]]})  # route 不足 2 点
    acks = [json.loads(p) for p in sender.payloads_of("cmd/ack")]
    assert acks[0]["data"]["code"] == 1
    assert "至少 2 点" in acks[0]["data"]["msg"]
    assert tasks == []


def test_routeurl_ignored(tmp_path):
    """旧版 routeUrl 字段不再读取（协议 §6.2 起内联，v0.4 未变更）。"""
    sender, tasks, handler = harness(tmp_path)
    handle(handler, "t3", {"routeUrl": "http://x/r.json",
                           "route": [[1.0, 1.0], [2.0, 2.0]]})
    assert len(tasks) == 1
    assert all("routeUrl" not in t for t in tasks)


def test_task_sink_exception_still_acks(tmp_path):
    sender, tasks, handler = harness(tmp_path)

    def bad_sink(parsed):
        raise RuntimeError("发布失败")

    handler.task_sink = bad_sink
    handle(handler, "t4", {"route": [[1.0, 1.0], [2.0, 2.0]]})
    acks = [json.loads(p) for p in sender.payloads_of("cmd/ack")]
    assert acks[0]["data"]["code"] == 0
