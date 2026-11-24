"""PPDV 车载端通信上报节点。

职责（PPDVedge/comm）：经 MQTT（ppdv/v1/{deviceId}/...）与云平台 PPDVapi 交换
遥测与指令，含断网缓存补传、下行去重与 cmd/ack 回执。协议见仓库根
docs/protocol.md（v0.4）。

本模块只做参数读取、订阅接线与生命周期管理；传输/上行/下行/缓存逻辑在
protocol/store/uplink/downlink/mqtt_link/engine/sources，不 import rclpy。

审核修正落点：
- device_secret/device_id 真参数化（env 优先），secret 为空以非零码退出；
- source 合法值校验；stale_cmd_max_age_s 负值 clamp 到 0；
- log_level 参数接线（节点日志级别），log_messages 走 INFO 级消息；
- 下行经 SerialExecutor 串行执行（防旧指令晚于 ESTOP 落地），ESTOP 在
  MQTT 回调线程经 handle_estop 置抢占门后立即执行（HTTP 阻塞换安全序）；
- ros 分支同样注入 handler.actions/spray（拒绝语义 ack code=1）；
- work/progress 订阅走 RosStateSource 的不可变合并（防撕裂/NameError）；
- 关停顺序：track.shutdown（停跟踪线程并结束会话）→ engine.stop → handler.shutdown
  → publish_offline → disconnect → loop_stop → executor.shutdown；simulate_offline 时也发显式 offline。
"""
import os


def _env(name: str) -> str:
    return os.environ.get(name, "")


def main(args=None):
    import rclpy
    from rclpy.node import Node

    from ppdv_comm.config import (
        CommConfig, TRACK_LOST_TIMEOUT_MS_RANGE, TRACK_MAX_TRAJECTORY_POINTS_RANGE,
        TRACK_MAX_TRACKS_PER_MESSAGE_RANGE, TRACK_SESSION_IDLE_S_RANGE,
        TRACK_TRAJECTORY_SAMPLE_MS_RANGE, TRACK_WINDOW_S_RANGE, clamp, clamp_rate,
    )
    from ppdv_comm.downlink import DownlinkHandler, SerialExecutor
    from ppdv_comm.engine import TelemetryEngine
    from ppdv_comm.mqtt_link import EdgeMqttClient
    from ppdv_comm.store import OfflineCache
    from ppdv_comm.uplink import Uplink

    rclpy.init(args=args)
    node = Node("ppdv_comm_node")

    # ---- 参数（凭据允许环境变量覆盖；缺项拒绝启动，退出码非 0） ----
    def p_str(name, default):
        node.declare_parameter(name, default)
        return str(node.get_parameter(name).value)

    def p_int(name, default):
        node.declare_parameter(name, default)
        return int(node.get_parameter(name).value)

    def p_float(name, default):
        node.declare_parameter(name, default)
        return float(node.get_parameter(name).value)

    def p_bool(name, default):
        node.declare_parameter(name, default)
        return bool(node.get_parameter(name).value)

    def _log_level_param():
        node.declare_parameter("log_level", "info")
        return str(node.get_parameter("log_level").value)

    # env 优先（无密钥进容器/车机的常见注入方式），参数兜底
    device_id = _env("PPDV_DEVICE_ID") or p_str("device_id", CommConfig.device_id)
    device_secret = _env("PPDV_DEVICE_SECRET") or p_str("device_secret",
                                                        CommConfig.device_secret)
    cfg = CommConfig(
        broker_host=p_str("broker_host", CommConfig.broker_host),
        broker_port=p_int("broker_port", CommConfig.broker_port),
        broker_tls=p_bool("broker_tls", CommConfig.broker_tls),
        keepalive=p_int("keepalive", CommConfig.keepalive),
        device_id=device_id,
        device_secret=device_secret,
        fw=p_str("fw", CommConfig.fw),
        tele_rate_hz=clamp_rate(p_float("tele_rate_hz", CommConfig.tele_rate_hz)),
        log_messages=p_bool("log_messages", CommConfig.log_messages),
        source=p_str("source", CommConfig.source).strip().lower(),
        sim_base_url=p_str("sim_base_url", CommConfig.sim_base_url),
        sim_poll_hz=max(1.0, min(5.0, p_float("sim_poll_hz", CommConfig.sim_poll_hz))),
        stale_cmd_max_age_s=max(0, p_int("stale_cmd_max_age_s",
                                         CommConfig.stale_cmd_max_age_s)),
        cache_dir=p_str("cache_dir", CommConfig.cache_dir),
        ros_topic_state=p_str("ros_topic_state", CommConfig.ros_topic_state),
        ros_topic_work=p_str("ros_topic_work", CommConfig.ros_topic_work),
        ros_topic_zone=p_str("ros_topic_zone", CommConfig.ros_topic_zone),
        ros_topic_alarm=p_str("ros_topic_alarm", CommConfig.ros_topic_alarm),
        ros_topic_progress=p_str("ros_topic_progress", CommConfig.ros_topic_progress),
        # 跟踪上行（clamp 范围见 config.py 的 TRACK_*_RANGE）
        track_enabled=p_bool("track_enabled", CommConfig.track_enabled),
        ros_topic_track_events=p_str("ros_topic_track_events",
                                     CommConfig.ros_topic_track_events),
        track_window_s=clamp(p_float("track_window_s", CommConfig.track_window_s),
                             *TRACK_WINDOW_S_RANGE),
        track_lost_timeout_ms=clamp(
            p_int("track_lost_timeout_ms", CommConfig.track_lost_timeout_ms),
            *TRACK_LOST_TIMEOUT_MS_RANGE),
        track_session_idle_s=clamp(
            p_int("track_session_idle_s", CommConfig.track_session_idle_s),
            *TRACK_SESSION_IDLE_S_RANGE),
        track_trajectory_sample_ms=clamp(
            p_int("track_trajectory_sample_ms", CommConfig.track_trajectory_sample_ms),
            *TRACK_TRAJECTORY_SAMPLE_MS_RANGE),
        track_max_trajectory_points=clamp(
            p_int("track_max_trajectory_points", CommConfig.track_max_trajectory_points),
            *TRACK_MAX_TRAJECTORY_POINTS_RANGE),
        track_max_tracks_per_message=clamp(
            p_int("track_max_tracks_per_message",
                  CommConfig.track_max_tracks_per_message),
            *TRACK_MAX_TRACKS_PER_MESSAGE_RANGE),
    )

    def fail(msg: str):
        node.get_logger().error(msg)
        node.destroy_node()
        if rclpy.ok():
            rclpy.shutdown()
        raise SystemExit(2)

    if not cfg.device_id:
        fail("device_id 为空：请用 ROS2 参数或环境变量 PPDV_DEVICE_ID 注入设备编号")
    if not cfg.device_secret:
        fail("device_secret 为空：请用 ROS2 参数（-p device_secret:=...）或环境变量 "
             "PPDV_DEVICE_SECRET 注入设备密钥（绝不硬编码）")
    if cfg.source not in ("sim", "ros"):
        fail(f"source={cfg.source} 非法：仅支持 sim（模拟器 REST）或 ros（真车话题）")

    from rclpy.logging import get_logger
    try:
        level = _log_level_param()
        if level:
            get_logger("ppdv_comm_node").set_level(level)
    except Exception:
        pass  # 非法级别保持默认，不阻塞启动

    def log(msg):
        node.get_logger().info(msg)   # 操作摘要：无条件 INFO

    def trace(msg):
        if cfg.log_messages:
            node.get_logger().info(f"[trace] {msg}")  # 报文级日志：受开关控制

    # ---- 接线 ----
    cache = OfflineCache(cfg.cache_dir, cfg.device_id, log=log)
    executor = SerialExecutor(log=log)
    client = EdgeMqttClient(cfg.device_id, cfg.device_secret, cfg.broker_host,
                            cfg.broker_port, on_downlink=lambda k, e: handler.handle(k, e),
                            keepalive=cfg.keepalive, tls=cfg.broker_tls,
                            log=log, trace=trace)
    uplink = Uplink(cfg.device_id, client, cache, cfg.fw, log=log, trace=trace)
    uplink.rate = cfg.tele_rate_hz

    task_pub = node.create_publisher(_json_msg_type(), "ppdv/task/assigned", 10)

    def task_sink(parsed):
        import json as _json
        msg = _json_msg_type()()
        msg.data = _json.dumps(parsed, ensure_ascii=False)
        task_pub.publish(msg)
        log(f"[任务] 已发布导航话题 ppdv/task/assigned（{len(parsed['route'])} 点）")

    handler = DownlinkHandler(uplink, executor, task_sink=task_sink,
                              stale_cmd_max_age_s=cfg.stale_cmd_max_age_s,
                              log=log, trace=trace)

    if cfg.source == "ros":
        source = _build_ros_source(node, cfg, log)
    else:
        from ppdv_comm.sources.sim_rest import SimRestSource
        source = SimRestSource(cfg.sim_base_url, log=log)
        if cfg.sim_poll_hz > cfg.tele_rate_hz:
            # 轮询在遥测轮内完成（engine 每轮一次 REST），实际轮询频率=遥测频率
            log(f"[sim] sim_poll_hz={cfg.sim_poll_hz} 高于遥测频率 {cfg.tele_rate_hz}，"
                f"实际按遥测频率轮询")

    # 两个数据源统一注入下行执行接口（ros 源未接真车控制前返回拒绝，ack code=1）
    handler.actions = source.apply_command
    handler.spray = source.apply_spray

    engine = TelemetryEngine(source, uplink, log=log, trace=trace)

    def on_connected():
        uplink.publish_online()
        log("[status] 已上线（retained），订阅 5 个下行主题")

    client.on_connected_cb = on_connected

    # ---- tele/track 跟踪上行（开关默认关闭；与 source 无关） ----
    track = None
    if cfg.track_enabled:
        track_topic = cfg.ros_topic_track_events.strip()
        if not track_topic:
            node.get_logger().error(
                "track_enabled=true 但 ros_topic_track_events 为空：跟踪上行保持关闭"
                "（遥测与下行照常运行）")
        else:
            from ppdv_comm.track import TrackAggregator

            track = TrackAggregator(
                cfg.device_id, uplink.publish_track,
                window_s=cfg.track_window_s,
                lost_timeout_ms=cfg.track_lost_timeout_ms,
                session_idle_s=cfg.track_session_idle_s,
                trajectory_sample_ms=cfg.track_trajectory_sample_ms,
                max_trajectory_points=cfg.track_max_trajectory_points,
                max_tracks_per_message=cfg.track_max_tracks_per_message,
                log=log, trace=trace,
            )
            # 队列深度 200：避免相机帧率突发丢事件；回调只把原文入队，
            # 由 track 内部线程串行处理（解析/聚合/PUBACK 等待都不在 ROS 执行器上）。
            node.create_subscription(_json_msg_type(), track_topic,
                                     lambda msg: track.post(msg.data), 200)
            log(f"[track] 跟踪上行已启用：事件话题 {track_topic} | 窗口 {cfg.track_window_s}s "
                f"| 丢失超时 {cfg.track_lost_timeout_ms}ms | 会话空闲 {cfg.track_session_idle_s}s "
                f"| 窗口线程 500ms")

    # ---- simulate_offline ROS2 服务（断网演练） ----
    from std_srvs.srv import SetBool

    def on_net_off(req, resp):
        uplink.set_simulate_offline(req.data)
        resp.success = True
        resp.message = "已模拟断网（缓存）" if req.data else "已恢复联网（按序补传）"
        return resp

    node.create_service(SetBool, "ppdv/comm/simulate_offline", on_net_off)

    # ---- ESTOP 快速通道：置抢占门后在回调线程立即执行（最高优先级；
    #      排队中的 START/RESUME/RTL 会被门拒绝，防急停被旧指令静默解除） ----
    _install_estop_fastpath(handler)

    client.start()
    engine.start()
    if track is not None:
        track.start()               # 独立窗口线程（不占用 ROS 执行器）
    log(f"ppdv_comm 启动 | 设备 {cfg.device_id} | EMQX {cfg.broker_host}:{cfg.broker_port} "
        f"| 源 {cfg.source} | 遥测 {uplink.rate}Hz | 下行串行执行器已启动")

    try:
        rclpy.spin(node)
    finally:
        if track is not None:
            # shutdown 内部先停窗口线程，再尽力结束会话（离线时结束序列进缓存）
            track.shutdown()
            log(f"[track] 汇总 {track.stats()}")
        engine.stop()
        handler.shutdown()          # 取消点喷自动停定时器
        if client.is_connected():
            # simulate_offline 时也发显式 offline（审核修正：不留残留 ONLINE；
            # 真断网时 MQTT 层发不出，无副作用）
            uplink.publish_offline()
            import time
            time.sleep(0.3)         # 等 offline 消息发出
        client.stop()               # disconnect → loop_stop（顺序修正）
        executor.shutdown()         # 排空下行队列
        node.destroy_node()
        if rclpy.ok():
            rclpy.shutdown()


def _json_msg_type():
    from std_msgs.msg import String
    return String


def _build_ros_source(node, cfg, log):
    """source=ros：订阅参数化真车话题 → RosStateSource 缓冲（骨架，假定见 README）。"""
    from ppdv_comm.sources.ros_state import RosStateSource

    source = RosStateSource(node, topics={
        "state": cfg.ros_topic_state, "work": cfg.ros_topic_work,
        "zone": cfg.ros_topic_zone, "alarm": cfg.ros_topic_alarm,
        "progress": cfg.ros_topic_progress,
    }, log=log)

    # 真车消息类型未定（ppdv_msgs 未创建）：以 JSON String 话题过渡，
    # 字段名与协议 §5.2/5.3/5.5 一致；接真车时改此处适配即可。
    from std_msgs.msg import String

    def _parse(dflt):
        def cb(msg):
            import json
            try:
                return json.loads(msg.data)
            except ValueError:
                log(f"[ros] {cfg.ros_topic_state if dflt is None else ''}话题坏包，忽略")
                return None
        return cb

    def on_state_msg(msg):
        import json
        try:
            d = json.loads(msg.data)
        except ValueError:
            log("[ros] state 话题坏包，忽略")
            return
        source.on_state(_state_from_json(d))

    node.create_subscription(String, cfg.ros_topic_state, on_state_msg, 10)
    node.create_subscription(String, cfg.ros_topic_work,
                             lambda m: _on_work_msg(source, m, log), 10)
    node.create_subscription(
        String, cfg.ros_topic_zone,
        lambda m: source.on_zone(m.data), 10)
    node.create_subscription(
        String, cfg.ros_topic_alarm,
        lambda m: _on_alarm_msg(source, m, log), 10)
    node.create_subscription(
        String, cfg.ros_topic_progress,
        lambda m: _on_progress_msg(source, m, log), 10)
    log(f"[ros] 已订阅真车话题（JSON String 过渡）：state={cfg.ros_topic_state} "
        f"work={cfg.ros_topic_work} zone={cfg.ros_topic_zone} "
        f"alarm={cfg.ros_topic_alarm} progress={cfg.ros_topic_progress}")
    return source


def _state_from_json(d: dict):
    """state JSON → VehicleSnapshot（lat/lon 非数值保留 None，不造 0）。"""
    from ppdv_comm.sources.base import VehicleSnapshot
    from ppdv_comm.sources.sim_rest import _f
    return VehicleSnapshot(
        lat=_f(d.get("lat")), lon=_f(d.get("lon")),
        alt=_f(d.get("alt")), heading=_f(d.get("heading")),
        speed=_f(d.get("speed")),
        rtk_fix=str(d["rtkFix"]) if d.get("rtkFix") is not None else None,
        mode=str(d["mode"]) if d.get("mode") is not None else None,
        battery=_i(d.get("battery")),
        task_id=str(d["taskId"]) if d.get("taskId") else None,
        spraying=bool(d.get("spraying", False)),
        flow=_f(d.get("flow")), pressure=_f(d.get("pressure")),
        tank_level=_i(d.get("tankLevel")), sprayed_area=_f(d.get("sprayedArea")),
        dosage=_f(d.get("dosage")),
        task_status=str(d["taskStatus"]) if d.get("taskStatus") else None,
        progress=_i(d.get("progress")), covered_area=_f(d.get("coveredArea")),
    )


def _on_work_msg(source, msg, log):
    import json
    try:
        d = json.loads(msg.data)
    except ValueError:
        log("[ros] work 话题坏包，忽略")
        return
    source.on_work(d)


def _on_progress_msg(source, msg, log):
    import json
    try:
        d = json.loads(msg.data)
    except ValueError:
        log("[ros] progress 话题坏包，忽略")
        return
    source.on_progress(d)


def _on_alarm_msg(source, msg, log):
    import json
    try:
        d = json.loads(msg.data)
    except ValueError:
        log("[ros] alarm 话题坏包，忽略")
        return
    source.on_alarm(str(d.get("code", "")), str(d.get("level", "WARN")),
                    str(d.get("msg", "")), d.get("lat"), d.get("lon"))


def _install_estop_fastpath(handler):
    """ESTOP 抢占：跳过执行器队列，在 MQTT 回调线程置抢占门后立即执行。

    抢占门语义见 downlink.DownlinkHandler.handle_estop：门内排队的
    START/RESUME/RTL 会被拒绝（ack code=1），ESTOP 执行完成即开门。
    """
    origin_handle = handler.handle

    def handle_with_estop(kind, env):
        data = env.get("data") if isinstance(env.get("data"), dict) else {}
        cmd = str(data.get("cmd") or "").upper()
        if kind == "cmd/req" and cmd == "ESTOP":
            handler.trace(f"[RX] {kind} {env}")
            handler.handle_estop(str(env.get("msgId") or ""), data)
            return
        origin_handle(kind, env)

    handler.handle = handle_with_estop


def _i(v):
    from ppdv_comm.sources.sim_rest import _i as _to_int
    return _to_int(v)


if __name__ == "__main__":
    main()
