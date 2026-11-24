"""sim 数据源：快照组装、zone 本地判定、REST 映射（HTTP 用桩替换）。"""
import json
from urllib.error import URLError

from ppdv_comm.sources import sim_rest
from ppdv_comm.sources.base import locate_zone
from ppdv_comm.sources.sim_rest import SimRestSource

STATE_RESPONSE = {
    "ok": True, "msg": "OK",
    "data": {
        "mode": "AUTO", "lat": 30.123456, "lon": 120.654321, "alt": 12.3,
        "heading": 90.0, "speed": 1.2, "rtkFix": "FIX", "battery": 88,
        "tank": 80, "spraying": True, "flow": 2.4, "pressure": 0.3,
        "sprayedArea": 1.85, "dosage": 3.1, "taskId": "T-1",
        "taskStatus": "RUNNING", "progress": 42, "coveredArea": 8.6,
        "zones": [
            {"zoneCode": "A1", "latMin": 30.0, "latMax": 31.0,
             "lonMin": 120.0, "lonMax": 121.0},
            {"zoneCode": "B2", "latMin": 40.0, "latMax": 41.0,
             "lonMin": 130.0, "lonMax": 131.0},
        ],
    },
}


def make_source(logs=None):
    logs = logs if logs is not None else []
    src = SimRestSource("http://sim:8090", log=logs.append)
    return src, logs


def stub_get(src, value):
    src._get_json = lambda path: value


def stub_post(src, recorder):
    def fake_post(path, body):
        recorder.append((path, body))
        return {"ok": True, "msg": "done", "data": None}
    src._post_json = fake_post


def test_poll_snapshot_maps_all_fields():
    src, _ = make_source()
    stub_get(src, STATE_RESPONSE)
    snap = src.poll_snapshot()
    assert snap.lat == 30.123456 and snap.lon == 120.654321
    assert snap.mode == "AUTO" and snap.battery == 88
    assert snap.rtk_fix == "FIX" and snap.speed == 1.2
    assert snap.spraying is True and snap.tank_level == 80
    assert snap.task_id == "T-1" and snap.task_status == "RUNNING"
    assert snap.progress == 42 and snap.covered_area == 8.6
    assert snap.flow == 2.4 and snap.dosage == 3.1


def test_poll_snapshot_none_on_error():
    logs = []
    src = SimRestSource("http://sim:8090", log=logs.append)
    src._get_json = lambda path: None
    assert src.poll_snapshot() is None
    assert logs == []  # GET 桩返回 None 直接跳过（无日志）


def test_zone_change_transitions():
    src, _ = make_source()
    stub_get(src, STATE_RESPONSE)
    assert src.take_zone_change() is None
    src.poll_snapshot()                    # 进入 A1（30.12,120.65 在 A1 包围盒内）
    assert src.take_zone_change() == "A1"
    src.poll_snapshot()                    # 无变化
    assert src.take_zone_change() is None
    # 走出所有区域
    resp = json.loads(json.dumps(STATE_RESPONSE))
    resp["data"]["lat"], resp["data"]["lon"] = 50.0, 50.0
    stub_get(src, resp)
    src.poll_snapshot()
    assert src.take_zone_change() is None  # 不命中不报（对齐模拟器语义）


def test_locate_zone_helper():
    zones = [{"zoneCode": "A1", "latMin": 30.0, "latMax": 31.0,
              "lonMin": 120.0, "lonMax": 121.0}]
    assert locate_zone(30.5, 120.5, zones) == "A1"
    assert locate_zone(31.0, 121.0, zones) == "A1"     # 边界含值
    assert locate_zone(31.5, 120.5, zones) is None
    assert locate_zone(30.5, 120.5, [{"latMin": 1}]) is None  # 缺键跳过


def test_command_mapping_via_local():
    src, _ = make_source()
    calls = []
    stub_post(src, calls)
    ok, msg = src.apply_command("START", {"speed": 1.0})
    assert ok and msg == "done"
    assert calls == [("/api/command", {"cmd": "START", "via": "local",
                                       "args": {"speed": 1.0}})]


def test_spray_mapping_start_stop_rename():
    src, _ = make_source()
    calls = []
    stub_post(src, calls)
    ok1, _ = src.apply_spray("start", {"areaCode": "A1"})
    ok2, _ = src.apply_spray("stop", {})
    assert ok1 and ok2
    assert calls == [("/api/spray", {"action": "on"}),      # start→on
                     ("/api/spray", {"action": "off"})]     # stop→off


def test_spray_unknown_action_not_forwarded():
    src, _ = make_source()
    calls = []
    stub_post(src, calls)
    ok, msg = src.apply_spray("refill", {})
    assert ok is False
    assert calls == []  # refill 只在模拟器页面用，协议无此 action


def test_rest_failure_returns_rejection():
    src, logs = make_source()
    def fail_post(path, body):
        return None
    src._post_json = fail_post
    ok, msg = src.apply_command("START", None)
    assert ok is False and "无响应" in msg


def test_real_http_error_paths():
    """真实 urllib 错误路径（不连网）：GET/POST 异常被吞并返回 None。"""
    src, logs = make_source()
    orig = sim_rest.urllib.request.urlopen

    def boom(req, timeout=None):
        raise URLError("connection refused")

    sim_rest.urllib.request.urlopen = boom
    try:
        assert src._get_json("/api/state") is None
        assert src._post_json("/api/command", {}) is None
    finally:
        sim_rest.urllib.request.urlopen = orig
    assert any("GET /api/state 失败" in m for m in logs)
    assert any("POST /api/command 失败" in m for m in logs)
