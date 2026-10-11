# PPDVedge · 车载端（无人车本体）

运行在无人车工控机 / 嵌入式板卡上的实时控制软件，负责感知、定位导航、运动与喷洒控制、安全防护，并通过 MQTT / 4G-5G 与云平台（`PPDVapi`）交换遥测数据与控制指令。

> 强实时系统，物理上独立于云端 Web 系统。技术栈：ROS2（C++ / Python 节点）。

## 子模块

| 目录 | 模块 | 职责 |
|------|------|------|
| [perception/](perception/) | 环境感知 | 传感器采集与融合，障碍物检测、作物行识别 |
| [navigation/](navigation/) | 定位导航 | RTK 定位 + SLAM，路径规划与跟踪 |
| [control/](control/) | 运动控制 | 底盘驱动、转向、速度闭环 |
| [spray/](spray/) | 喷洒作业控制 | 喷头、流量/压力、变量喷洒 |
| [safety/](safety/) | 安全防护 | 急停、越界、避障、故障诊断（优先级最高） |
| [comm/](comm/) | 通信上报 | 与云平台的遥测上报与指令接收 |

## 硬件接入记录

Atlas 200I DK A2 上的 ROS2 Humble 环境、N10P 激光雷达、Astra Pro Plus 深度相机和 SLAM 建图实测记录见 [docs/hardware/](docs/hardware/README.md)。

详见 [../docs/architecture.md](../docs/architecture.md)。
