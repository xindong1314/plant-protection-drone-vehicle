"""通信节点配置：ROS2 参数值收敛为带默认值的不可变配置对象。

只承载传输/业务语义（broker/device/cache_dir/stale_cmd_max_age_s/log_messages
等取值）；模拟器侧的仿真参数（电量/走位/初始坐标等）不在此处理。凭据
（device_secret）绝不在此硬编码，由节点启动参数或环境变量注入。
"""
from dataclasses import dataclass

# tele/track 参数范围（comm_node 读取参数与 TrackAggregator 自校验共用同一份字面量，
# 避免两处各写一遍、改一处漂一处）。
TRACK_WINDOW_S_RANGE = (1.0, 5.0)
TRACK_LOST_TIMEOUT_MS_RANGE = (500, 10000)
TRACK_SESSION_IDLE_S_RANGE = (0, 3600)
TRACK_TRAJECTORY_SAMPLE_MS_RANGE = (50, 5000)
TRACK_MAX_TRAJECTORY_POINTS_RANGE = (2, 20)
TRACK_MAX_TRACKS_PER_MESSAGE_RANGE = (1, 50)
TRACK_TICK_INTERVAL_S_RANGE = (0.05, 5.0)


def clamp(value, minimum, maximum):
    """把数值收敛到 [minimum, maximum]（参数/直接构造共用）。"""
    return max(minimum, min(maximum, value))


@dataclass(frozen=True)
class CommConfig:
    # MQTT 连接（协议 §2）
    broker_host: str = "localhost"
    broker_port: int = 1883
    broker_tls: bool = False
    keepalive: int = 30
    # 设备（一机一密）
    device_id: str = "PPDV-0001"
    device_secret: str = ""
    fw: str = "1.3.0"
    # 上行
    tele_rate_hz: float = 1.0
    log_messages: bool = True
    # 数据源
    source: str = "sim"
    sim_base_url: str = "http://127.0.0.1:8090"
    sim_poll_hz: float = 2.0
    # 下行
    stale_cmd_max_age_s: int = 300
    # 断网缓存
    cache_dir: str = "cache"
    # 真车 ROS2 数据源话题（source=ros 时）
    ros_topic_state: str = "/ppdv/vehicle/state"
    ros_topic_work: str = "/ppdv/vehicle/work"
    ros_topic_zone: str = "/ppdv/vehicle/zone"
    ros_topic_alarm: str = "/ppdv/vehicle/alarm"
    ros_topic_progress: str = "/ppdv/vehicle/progress"
    # 跟踪上行 tele/track（协议 §5.6；默认关闭，与 source 无关）
    track_enabled: bool = False
    ros_topic_track_events: str = "/ppdv/perception/track_events"
    track_window_s: float = 2.0
    track_lost_timeout_ms: int = 2000
    track_session_idle_s: int = 120
    track_trajectory_sample_ms: int = 500
    track_max_trajectory_points: int = 20
    track_max_tracks_per_message: int = 50


def clamp_rate(hz: float) -> float:
    """遥测频率收敛到 0.1~10 Hz。"""
    return max(0.1, min(10.0, float(hz)))
