"""sim 数据源：轮询小车模拟器 REST，并把下行指令映射为模拟器接口。

模拟器：本地模拟器服务（端口 8090），接口契约见其
README「REST 与 WS 接口一览」。联调前提（写进 comm/README.md）：
- 模拟器以「纯本地模式」运行（config.yaml 的 broker 指向不可达端口即可，
  页面显示未连接），由 ppdv_comm 独占 MQTT 连接，避免同 clientId 互踢；
- 模拟器的 /api/dup、/api/bad 是它自己上行链路的注入接口，本次用不上。

REST 映射：
- cmd/req   → POST /api/command {cmd, via:"local", args}（via 必须 local，避免回环）
- spray/req → POST /api/spray {action:"on"|"off"}（改名：start→on、stop→off）
- config/set → teleRateHz 调本节点遥测频率（不 POST /api/rate）
- task/assign → 不转发（模拟器纯本地模式收不到 MQTT；/api/task 只是推进当前任务的接口）
本模块不 import rclpy。
"""
import json
import threading
import urllib.error
import urllib.request

from .base import VehicleSnapshot, locate_zone

TIMEOUT_S = 3.0


def _noop(msg):
    pass


class SimRestSource:

    def __init__(self, base_url: str, log=None):
        self.base_url = base_url.rstrip("/")
        self.log = log or _noop
        self._zone_code = None   # 当前区域（变化时由 engine 经 take_zone_change 取走）
        self._lock = threading.Lock()

    # ---- 状态采集（engine 每 tick 调用） ----

    def poll_snapshot(self) -> VehicleSnapshot:
        """GET /api/state 组装快照；失败返回 None（本轮跳过，不产生假数据）。"""
        raw = self._get_json("/api/state")
        if raw is None:
            return None
        data = raw.get("data") if isinstance(raw, dict) else None
        if not isinstance(data, dict):
            return None
        return self._to_snapshot(data)

    def _to_snapshot(self, d: dict) -> VehicleSnapshot:
        # lat/lon 非数值时保留 None（引擎按缺失跳过该轮 state），不回填 0.0
        #（云侧只把"非数值"当缺失，数值 0.0 会被当真实坐标写进设备快照/路线录制）
        snap = VehicleSnapshot(
            lat=_f_or_none(d.get("lat")),
            lon=_f_or_none(d.get("lon")),
            alt=_f(d.get("alt")),
            heading=_f(d.get("heading")),
            speed=_f(d.get("speed")),
            rtk_fix=_s(d.get("rtkFix")),
            mode=_s(d.get("mode")),
            battery=_i(d.get("battery")),
            task_id=_s(d.get("taskId")) or None,
            spraying=bool(d.get("spraying")),
            flow=_f(d.get("flow")),
            pressure=_f(d.get("pressure")),
            tank_level=_i(d.get("tank")),
            sprayed_area=_f(d.get("sprayedArea")),
            dosage=_f(d.get("dosage")),
            task_status=_s(d.get("taskStatus")) or None,
            progress=_i(d.get("progress")),
            covered_area=_f(d.get("coveredArea")),
            zones=_zones(d.get("zones")),
        )
        with self._lock:
            zone = locate_zone(snap.lat, snap.lon, snap.zones)
            if zone != self._zone_code:
                self._zone_code = zone
                self._zone_changed = True
        return snap

    def take_zone_change(self) -> str:
        """区域变化时返回新 zoneCode，否则 None。"""
        with self._lock:
            changed = getattr(self, "_zone_changed", False)
            self._zone_changed = False
            return self._zone_code if changed and self._zone_code else None

    # ---- 下行 → REST 映射 ----

    def apply_command(self, cmd: str, args) -> tuple:
        """cmd/req → POST /api/command {cmd, via:"local", args}。"""
        body = {"cmd": cmd, "via": "local", "args": args}
        raw = self._post_json("/api/command", body)
        return _rest_result(raw, fallback_fail=f"模拟器拒绝 {cmd}")

    def apply_spray(self, action: str, data: dict) -> tuple:
        """spray/req → POST /api/spray {action:"on"|"off"}（start→on、stop→off）。"""
        if action == "start":
            body = {"action": "on"}
        elif action == "stop":
            body = {"action": "off"}
        else:
            return False, f"未知 action={action}"
        raw = self._post_json("/api/spray", body)
        return _rest_result(raw, fallback_fail="模拟器拒绝喷洒指令")

    # ---- HTTP ----

    def _get_json(self, path: str):
        try:
            with urllib.request.urlopen(self.base_url + path, timeout=TIMEOUT_S) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except (urllib.error.URLError, OSError, ValueError) as e:
            self.log(f"[sim] GET {path} 失败: {e}")
            return None

    def _post_json(self, path: str, body: dict):
        try:
            req = urllib.request.Request(
                self.base_url + path,
                data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                headers={"Content-Type": "application/json"}, method="POST")
            with urllib.request.urlopen(req, timeout=TIMEOUT_S) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except (urllib.error.URLError, OSError, ValueError) as e:
            self.log(f"[sim] POST {path} 失败: {e}")
            return None


def _rest_result(raw, fallback_fail: str) -> tuple:
    """模拟器统一响应 {ok, msg} → (ok, msg)；请求失败回 (False, 原因)。"""
    if isinstance(raw, dict) and "ok" in raw:
        return bool(raw.get("ok")), str(raw.get("msg") or fallback_fail)
    return False, fallback_fail + "（无响应）"


def _f(v):
    return float(v) if isinstance(v, (int, float)) and not isinstance(v, bool) else (
        _try_float(v))


def _f_or_none(v):
    """数值或可解析字符串 → float；否则 None（保留"缺失"语义，不造 0）。"""
    return _f(v)


def _try_float(v):
    if isinstance(v, str):
        try:
            return float(v.strip())
        except ValueError:
            return None
    return None


def _i(v):
    f = _f(v)
    return int(f) if f is not None else None


def _s(v):
    return str(v) if v is not None else None


def _zones(v):
    return v if isinstance(v, list) else []
