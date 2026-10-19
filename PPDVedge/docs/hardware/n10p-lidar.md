# N10P 激光雷达接入指南 - Atlas 200I DK A2

## 适用环境
- 板子：Atlas 200I DK A2
- 系统：Ubuntu 22.04 LTS (ARM64 / aarch64)
- ROS 版本：ROS 2 Humble
- 构建工具：colcon

前置条件请先完成：
- [ROS2 Humble 安装指南](ros2-humble-install.md)
- [ROS2 Colcon 安装指南](colcon-install.md)

## 1. 接线建议

### 推荐方案：USB 转串口接入
如果你的 N10P 带 USB 转接板，优先直接接到板子的 USB 口。

优点：
- 不容易接错 TX/RX
- 不容易出现串口电平不匹配
- Linux 下通常会识别为 `/dev/ttyUSB0` 或 `/dev/ttyACM0`

本次实测结果：
- 板子上同时出现了 `/dev/ttyUSB0` 和 `/dev/ttyACM0`
- `ttyUSB0` 可以读到原始字节，但驱动一直报 `error`
- 最终跑通的实际串口是 `/dev/ttyACM0`

### 直连 UART
如果你使用的是雷达原始串口，请注意：
- 雷达供电按原厂要求接，不要直接从板子串口取电
- 板子与雷达必须共地
- TX/RX 交叉连接
- 在没有确认串口电平前，不要直接硬连

推荐先用 USB 转串口方式跑通，再改为板载 UART。

## 2. 创建工作空间
```bash
mkdir -p ~/ros2_ws/src
cd ~/ros2_ws/src
```

## 3. 获取 N10P 对应 ROS 2 驱动
驱动仓库使用雷神激光官方 `Lslidar_ROS2_driver` 的 `M10P/N10P` 分支。

```bash
cd ~/ros2_ws/src
git clone -b "M10P/N10P" https://github.com/Lslidar/Lslidar_ROS2_driver.git
```

## 4. 安装依赖
```bash
sudo apt update
sudo apt install -y \
  git \
  build-essential \
  libpcap-dev \
  libpcl-dev \
  ros-humble-pcl-conversions \
  ros-humble-diagnostic-updater
```

如果你安装的是 `ros-humble-desktop`，`rviz2` 一般已经包含。

## 5. 编译驱动
```bash
cd ~/ros2_ws
source /opt/ros/humble/setup.bash
colcon build --symlink-install
```

编译完成后加载环境：
```bash
source ~/ros2_ws/install/setup.bash
```

## 6. 修改雷达参数
编辑参数文件：
```bash
cd ~/ros2_ws
nano src/Lslidar_ROS2_driver/lslidar_driver/params/lsx10.yaml
```

重点检查并修改以下参数：
```yaml
/lslidar_driver_node:
  ros__parameters:
    frame_id: laser_link
    lidar_name: N10_P
    interface_selection: serial
    serial_port_: /dev/ttyACM0
    pubScan: true
    pubPointCloud2: false
    scan_topic: /scan
```

说明：
- `lidar_name` 必须改成 `N10_P`
- `interface_selection` 使用 `serial`
- 本次实测可用的是 `serial_port_: /dev/ttyACM0`
- 更稳的写法可以使用 `/dev/serial/by-id/...`
- 纯导航场景先只开 `/scan`，这样更简单

## 7. 查找实际串口号
插上雷达后执行：
```bash
ls /dev/ttyUSB* /dev/ttyACM* 2>/dev/null
```

也可以看内核日志：
```bash
dmesg | grep -E "ttyUSB|ttyACM"
```

建议再看稳定设备名：
```bash
ls -l /dev/serial/by-id
```

本次实测识别结果：
```bash
usb-1a86_USB_Serial-if00-port0 -> ../../ttyUSB0
usb-1a86_USB_Single_Serial_5B21243862-if00 -> ../../ttyACM0
```

最终跑通使用的是：
```bash
/dev/ttyACM0
```

如果没有权限访问串口：
```bash
sudo usermod -aG dialout $USER
```

执行后重新登录一次系统。

## 8. 启动雷达驱动
```bash
cd ~/ros2_ws
source /opt/ros/humble/setup.bash
source install/setup.bash
ros2 launch lslidar_driver lslidar_launch.py
```

该启动文件会同时启动：
- `lslidar_driver_node`
- `rviz2`

如果你当前只有一个命令行终端，推荐后台启动：
```bash
cd ~/ros2_ws
source /opt/ros/humble/setup.bash
source install/setup.bash
nohup ros2 launch lslidar_driver lslidar_launch.py > /tmp/lslidar.log 2>&1 &
sleep 3
tail -n 50 /tmp/lslidar.log
```

## 9. 打开雷达
部分型号需要主动发送启动命令：
```bash
ros2 topic pub -1 /lslidar_order std_msgs/msg/Int8 "{data: 1}"
```

关闭雷达：
```bash
ros2 topic pub -1 /lslidar_order std_msgs/msg/Int8 "{data: 0}"
```

## 10. 验证是否正常出数
查看话题：
```bash
ros2 topic list
```

检查激光数据：
```bash
ros2 topic echo /scan --once
```

如果能看到 `ranges` 等字段，说明雷达已经正常工作。

本次实测成功示例：
```yaml
header:
  frame_id: laser_link
ranges:
- .inf
- 0.5960000157356262
- 0.609000027179718
- 0.6119999885559082
```

也可以看话题频率：
```bash
ros2 topic hz /scan
```

## 11. 常见问题

### 1. 启动后没有 `/scan`
优先检查：
- `lidar_name` 是否写成了 `N10_P`
- `serial_port_` 是否写对
- 当前用户是否有串口权限
- 是否已经发送了启动命令 `data: 1`
- 是否误把 `ttyUSB0` 当成了最终数据口，本次实测应使用 `ttyACM0`

### 2. RViz 打开了但没有点云/激光扇形
检查：
- `Fixed Frame` 是否与 `frame_id` 一致，例如 `laser_link`
- 是否真的有 `/scan` 数据

### 3. 串口节点不存在
通常是以下原因：
- USB 线只供电不传数据
- 板子未识别 USB 转串口芯片
- 雷达没有正常供电

### 4. Humble 下编译报错
官方仓库说明里写的是 Ubuntu 20.04 + ROS 2 Foxy，但这个分支已经是 ROS 2 驱动，通常可以作为 Humble 接入基础。

如果你在 Atlas 200I DK A2 上编译失败，建议保留报错日志，按错误项逐个修。常见方向有：
- `CMakeLists.txt` 依赖声明不完整
- `package.xml` 依赖缺失
- 新版编译器对旧代码更严格

### 5. `colcon build` 提示 Duplicate package names
如果工作空间里同时存在：
- `src/Lslidar_ROS2_driver`
- `src/lslidar_driver`

会报同名包冲突。处理方法：
- 只保留一套驱动源码
- 旧目录移出 `~/ros2_ws`
- 然后清理缓存再重新编译

```bash
rm -rf ~/ros2_ws/build ~/ros2_ws/install ~/ros2_ws/log
```

### 6. 编译时出现 `ModuleNotFoundError: No module named 'catkin_pkg'`
这通常不是驱动代码问题，而是 Python 环境被 `conda` 干扰了。

处理方法：
```bash
conda deactivate
apt update
apt install -y python3-catkin-pkg python3-empy python3-lark python3-colcon-common-extensions
```

然后重新编译：
```bash
cd ~/ros2_ws
rm -rf build install log
source /opt/ros/humble/setup.bash
colcon build --symlink-install
```

### 7. 日志里反复只有 `error`
如果驱动日志中持续出现：
```text
error
error
error
```

优先检查：
- 雷达是否真的在转
- 当前串口是否选对
- 先直接读取串口原始数据确认设备是否在发数

排查命令：
```bash
stty -F /dev/ttyACM0 460800 raw -echo
timeout 2 dd if=/dev/ttyACM0 bs=256 count=1 status=none | hexdump -C
```

如果能读到连续十六进制字节流，说明雷达正在发数据。

## 12. 本次实测最终可用配置
```yaml
/lslidar_driver_node:
  ros__parameters:
    frame_id: laser_link
    lidar_name: N10_P
    interface_selection: serial
    serial_port_: /dev/ttyACM0
    pubScan: true
    pubPointCloud2: false
    scan_topic: /scan
```

## 13. 推荐最小跑通流程
```bash
mkdir -p ~/ros2_ws/src
cd ~/ros2_ws/src
git clone -b "M10P/N10P" https://github.com/Lslidar/Lslidar_ROS2_driver.git

cd ~/ros2_ws
source /opt/ros/humble/setup.bash
sudo apt install -y libpcap-dev libpcl-dev ros-humble-pcl-conversions ros-humble-diagnostic-updater
colcon build --symlink-install
source install/setup.bash

sed -i 's/lidar_name: .*/lidar_name: N10_P/' src/Lslidar_ROS2_driver/lslidar_driver/params/lsx10.yaml
sed -i 's#serial_port_: .*#serial_port_: /dev/ttyACM0#' src/Lslidar_ROS2_driver/lslidar_driver/params/lsx10.yaml

ros2 launch lslidar_driver lslidar_launch.py
ros2 topic pub -1 /lslidar_order std_msgs/msg/Int8 "{data: 1}"
ros2 topic echo /scan --once
```

## 14. 后续要做的
雷达已经通了之后，建议按下面顺序继续做：

### 1. 验证稳定性
```bash
ros2 topic hz /scan
tail -n 50 /tmp/lslidar.log
```

确认：
- `/scan` 频率稳定
- 日志没有持续异常

### 2. 建立 TF
如果后面要做建图、定位、导航，需要保证 `laser_link` 正确挂到机器人坐标系上，至少要有：
- `base_link -> laser_link`

如果没有这条 TF，后续 `slam_toolbox`、`nav2` 都会出问题。

### 3. 接入 RViz
在 `rviz2` 里确认：
- `Fixed Frame` 设置正确
- 能看到 `/scan`
- 雷达方向和实际朝向一致

### 4. 接入建图
推荐先接 `slam_toolbox` 跑 2D 建图，再接定位和导航。

### 5. 接入导航
后续需要继续配置：
- 机器人底盘里程计
- `tf`
- 地图
- `nav2`

### 6. 做开机自启动
等确认稳定后，再考虑把雷达驱动写进：
- `systemd` 服务
- 或统一的机器人启动脚本

### 7. 做固定串口命名
如果后面 USB 设备增多，`ttyACM0` 可能变化。建议最终补一个 `udev` 规则，把雷达固定成自定义设备名，例如：
- `/dev/n10p_lidar`

## 15. 下次怎么快速连接这个雷达
下次接雷达时，按下面流程直接做。

### 快速检查设备
```bash
ls /dev/ttyUSB* /dev/ttyACM* 2>/dev/null
ls -l /dev/serial/by-id
```

确认有：
```bash
/dev/ttyACM0
```

### 快速启动
```bash
cd ~/ros2_ws
source /opt/ros/humble/setup.bash
source install/setup.bash
nohup ros2 launch lslidar_driver lslidar_launch.py > /tmp/lslidar.log 2>&1 &
sleep 3
ros2 topic pub -1 /lslidar_order std_msgs/msg/Int8 "{data: 1}"
ros2 topic echo /scan --once
```

### 快速判断是否连上
如果 `ros2 topic echo /scan --once` 能看到类似下面的数据，就说明连接成功：
```yaml
header:
  frame_id: laser_link
ranges:
- 0.5960000157356262
- 0.609000027179718
```

### 快速停掉驱动
```bash
pkill -f lslidar_driver_node
```

### 快速看日志
```bash
tail -n 100 /tmp/lslidar.log
```

## 16. 参考资料
- 雷神激光 ROS 2 驱动仓库：<https://github.com/Lslidar/Lslidar_ROS2_driver/tree/M10P/N10P>
- 启动说明见该分支 README
- 参数文件见 `lslidar_driver/params/lsx10.yaml`
