# Orbbec Astra Pro Plus 摄像头接入指南 - Atlas200I_DK_A2

## 适用环境
- 板子：Atlas 200I DK A2
- 系统：Ubuntu 22.04 LTS (ARM64 / aarch64)
- ROS 版本：ROS 2 Humble
- 构建工具：colcon
- 相机厂商：Orbbec
- 本次实际接入型号：Astra Pro Plus

前置条件请先完成：
- [ROS2 Humble 安装指南](ros2-humble-install.md)
- [ROS2 Colcon 安装指南](colcon-install.md)

## 先说结论

这次最初把设备误判成了 `Gemini Pro / Gemini 330`，这条路线是错的，已删除。

本次实机验证后的正确结论是：

- 当前接入的设备是 `Orbbec Astra Pro Plus`
- 应使用 `ros2_astra_camera`
- 不应使用 `orbbec_camera + gemini330_series.yaml`
- 这台设备枚举为 `USB 2.0 / 480M` 是正常现象，不要再按 `5000M / USB 3.x` 去排查
- 当前已验证 `深度 / 红外 / 点云` 可以正常发布

本次实测识别到的 USB 设备为：

```text
2bc5:0614 Orbbec ORBBEC Depth Sensor
2bc5:0511 Orbbec USB 2.0 Camera
```

`dmesg` 中也能看到：

```text
new high-speed USB device
Product: USB 2.0 Camera
Product: ORBBEC Depth Sensor
```

这说明当前设备按 `USB 2.0 High-Speed (480M)` 工作，对 `Astra Pro Plus` 来说这不是异常。

## 本次验证通过的 ROS 话题

启动成功后，已经确认可以看到：

```text
/camera/depth/camera_info
/camera/depth/image_raw
/camera/depth/points
/camera/ir/camera_info
/camera/ir/image_raw
/tf
/tf_static
```

同时日志显示：

```text
Device connected: Astra serial number: AY2755200LC
device  started.
```

## 错误路线，直接删除

下面这些判断或命令不适用于本次设备：

- `ros2 launch orbbec_camera orbbec_camera.launch.py config_file_path:=gemini330_series.yaml`
- 用 `gemini330_series.yaml` 判断是否启用彩色/深度流
- 把 `480M` 当成错误信号
- 把“只有 `/camera/device_status`”解释成 USB 3.0 链路没跑起来
- 按 `Gemini 330` 文档继续排查 `USB 3.0 Hub / 5000M`

这些内容都不再适用当前设备。

## 1. 安装依赖

```bash
sudo apt update
sudo apt install -y \
  git \
  cmake \
  build-essential \
  libgflags-dev \
  libgoogle-glog-dev \
  libusb-1.0-0-dev \
  libeigen3-dev \
  python3-empy \
  python3-lark-parser \
  python3-colcon-common-extensions \
  ros-humble-image-geometry \
  ros-humble-camera-info-manager \
  ros-humble-image-transport \
  ros-humble-image-publisher \
  v4l-utils
```

## 2. 先处理 Python / conda 环境

如果当前终端带有 `(base)`，先退出 conda，再编译，否则很容易出现：

```text
ModuleNotFoundError: No module named 'em'
```

建议执行：

```bash
conda deactivate || true
conda deactivate || true
unset PYTHONPATH
unset PYTHONHOME
which python3
python3 --version
```

正确目标应优先看到：

```text
/usr/bin/python3
```

而不是：

```text
/usr/local/miniconda3/bin/python3
```

## 3. 安装 `libuvc`

```bash
cd /root
git clone https://github.com/libuvc/libuvc.git
cd libuvc
mkdir -p build
cd build
cmake ..
make -j4
sudo make install
sudo ldconfig
```

## 4. 获取驱动源码

```bash
source /opt/ros/humble/setup.bash
mkdir -p ~/ros2_ws/src
cd ~/ros2_ws/src
git clone https://github.com/orbbec/ros2_astra_camera.git
```

## 5. 安装脚本和 udev 规则

```bash
cd ~/ros2_ws/src/ros2_astra_camera/astra_camera/scripts
sudo bash install.sh
sudo udevadm control --reload-rules
sudo udevadm trigger
```

## 6. 编译驱动

只编 `astra` 相关包：

```bash
source /opt/ros/humble/setup.bash
cd ~/ros2_ws
colcon build \
  --packages-select astra_camera_msgs astra_camera \
  --event-handlers console_direct+ \
  --cmake-args -DCMAKE_BUILD_TYPE=Release
```

编译完成后加载环境：

```bash
source ~/ros2_ws/install/setup.bash
```

## 7. 启动相机

前台启动：

```bash
source /opt/ros/humble/setup.bash
source ~/ros2_ws/install/setup.bash
ros2 launch astra_camera astra.launch.xml
```

如果只有一个终端，建议后台启动：

```bash
source /opt/ros/humble/setup.bash
source ~/ros2_ws/install/setup.bash
nohup ros2 launch astra_camera astra.launch.xml > /tmp/astra.log 2>&1 &
sleep 5
tail -n 80 /tmp/astra.log
```

说明：

- 当前版本里 `ros2 run astra_camera cleanup_shm_node` 不存在，可忽略
- 看到 `No executable found` 不影响相机启动

## 8. 验证是否启动成功

先看节点：

```bash
ros2 node list
```

本次实测看到：

```text
/camera/camera
```

再看话题：

```bash
ros2 topic list
```

本次实测看到：

```text
/camera/depth/camera_info
/camera/depth/image_raw
/camera/depth/points
/camera/ir/camera_info
/camera/ir/image_raw
/parameter_events
/rosout
/tf
/tf_static
```

进一步验证数据是否在发：

```bash
ros2 topic echo /camera/depth/camera_info --once
ros2 topic hz /camera/depth/image_raw
```

## 9. 本次日志中的关键现象

启动日志里出现了：

```text
Device connected: Astra serial number: AY2755200LC
device  started.
```

同时也出现了：

```text
color is not enable
```

这说明：

- 设备已经真正连上并启动
- 当前默认配置下彩色流没有开启
- 但深度、红外、点云已经正常可用

因此本次接入目标如果是避障、测距、点云处理，已经算跑通。

## 10. 当前最小跑通流程

```bash
conda deactivate || true
conda deactivate || true
unset PYTHONPATH
unset PYTHONHOME

source /opt/ros/humble/setup.bash

sudo apt update
sudo apt install -y \
  git cmake build-essential \
  libgflags-dev libgoogle-glog-dev libusb-1.0-0-dev libeigen3-dev \
  python3-empy python3-lark-parser python3-colcon-common-extensions \
  ros-humble-image-geometry ros-humble-camera-info-manager \
  ros-humble-image-transport ros-humble-image-publisher \
  v4l-utils

cd /root
git clone https://github.com/libuvc/libuvc.git
cd libuvc
mkdir -p build
cd build
cmake ..
make -j4
sudo make install
sudo ldconfig

mkdir -p ~/ros2_ws/src
cd ~/ros2_ws/src
git clone https://github.com/orbbec/ros2_astra_camera.git

cd ~/ros2_ws/src/ros2_astra_camera/astra_camera/scripts
sudo bash install.sh
sudo udevadm control --reload-rules
sudo udevadm trigger

cd ~/ros2_ws
source /opt/ros/humble/setup.bash
colcon build \
  --packages-select astra_camera_msgs astra_camera \
  --event-handlers console_direct+ \
  --cmake-args -DCMAKE_BUILD_TYPE=Release

source ~/ros2_ws/install/setup.bash
nohup ros2 launch astra_camera astra.launch.xml > /tmp/astra.log 2>&1 &
sleep 5
tail -n 80 /tmp/astra.log
ros2 node list
ros2 topic list
```

## 11. 停止相机

```bash
pkill -f "ros2 launch astra_camera"
pkill -f astra_camera_node
```

## 12. 快速判断是否真的跑通

快速标准不是看设备名，不是看 USB 速率，而是看这些数据是否存在：

```text
/camera/depth/image_raw
/camera/depth/points
/camera/ir/image_raw
```

如果这些话题都在，说明当前相机已经可用。

## 13. 参考资料

- `ros2_astra_camera`：<https://github.com/orbbec/ros2_astra_camera>
- Astra 系列产品页：<https://www.orbbec.com/product-astra/>

---
更新日期：2026-04-25
