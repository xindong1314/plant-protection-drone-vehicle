# 车载端硬件接入记录

记录在 Atlas 200I DK A2 开发板上搭建 ROS2 环境、接入激光雷达和深度相机、跑通 2D SLAM 建图的实机过程。内容为 2026 年 4–5 月的实测记录，是 `perception/`、`navigation/` 后续开发的硬件基线。

> 这些是手工验证记录，不是可编译的节点或 launch 文件；PPDVedge 的实现状态以 [模块台账](../../../docs/modules.md) 为准。

## 硬件与环境

| 项 | 型号 / 版本 | 备注 |
|---|---|---|
| 计算板 | Atlas 200I DK A2 | 主机名 `davinci-mini`，ARM64 / aarch64 |
| 系统 | Ubuntu 22.04 LTS | 自带 conda，编译前需退出 conda 环境 |
| ROS | ROS 2 Humble Desktop + colcon | 工作空间 `~/ros2_ws` |
| 激光雷达 | 镭神 N10P | 驱动 `Lslidar_ROS2_driver`（`M10P/N10P` 分支），实际串口 `/dev/ttyACM0` |
| 深度相机 | Orbbec Astra Pro Plus | 驱动 `ros2_astra_camera`，USB 2.0 枚举属正常 |

## 阅读顺序

| 顺序 | 文档 | 内容 |
|---:|---|---|
| 1 | [ros2-humble-install.md](ros2-humble-install.md) | ROS2 Humble 安装（清华镜像源） |
| 2 | [colcon-install.md](colcon-install.md) | colcon 安装、工作空间与常用编译命令 |
| 3 | [n10p-lidar.md](n10p-lidar.md) | N10P 激光雷达接线、驱动编译、参数与 `/scan` 验证 |
| 4 | [astra-pro-plus-camera.md](astra-pro-plus-camera.md) | Astra Pro Plus 驱动安装与深度 / 红外 / 点云验证 |
| 5 | [astra-depth-troubleshooting.md](astra-depth-troubleshooting.md) | 深度流无帧、设备占用、分辨率不支持等排障，以及远程可视化 |
| 6 | [camera-slam-bringup.md](camera-slam-bringup.md) | 相机 + 雷达 + 静态 TF + `slam_toolbox` 联调建图 |

## 文档间的差异

排障记录（第 5 篇）晚于第 4、6 篇，两者冲突时以排障记录为准：

- **深度流启动参数**：默认参数启动时可能话题存在但没有帧。建议显式指定支持的分辨率，例如 `depth_width:=320 depth_height:=200 depth_fps:=15`；`320x240`、`640x480` 不受支持。
- **板上可视化**：板子的 VNC/X11 环境下 `rviz2` 无法创建渲染窗口；`rqt_image_view` 需通过 `ros2 run rqt_image_view rqt_image_view` 并设置 `DISPLAY` 启动。长期调试建议在同一局域网的电脑上运行可视化工具。

## 尚未完成

- 底盘里程计 `/odom` 与 `odom -> base_link` 动态 TF（建图记录中为临时静态 TF）
- `base_link -> laser_link`、`base_link -> camera_link` 的实测安装外参
- 雷达串口的 `udev` 固定命名和驱动开机自启动
- RTK、IMU 等其余传感器接入，以及 `nav2` 定位导航
