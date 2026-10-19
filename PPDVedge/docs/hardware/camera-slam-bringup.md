# 摄像头出图与 SLAM 建图联调指南 - Atlas 200I DK A2

## 当前结论

- 摄像头型号按前次实测为 `Orbbec Astra Pro Plus`
- 摄像头驱动使用 `ros2_astra_camera`
- 已验证可用话题：
  - `/camera/depth/image_raw`
  - `/camera/depth/points`
  - `/camera/ir/image_raw`
- 这台设备默认日志会出现 `color is not enable`，所以不要先等彩色图像，先看深度图或红外图
- 2D SLAM 建图主输入建议使用 N10P 激光雷达的 `/scan`
- `slam_toolbox` 建图前必须保证 TF 链路完整，至少要有：
  - `base_link -> laser_link`
  - `odom -> base_link`

## 1. 启动摄像头

```bash
conda deactivate || true
conda deactivate || true
unset PYTHONPATH
unset PYTHONHOME

source /opt/ros/humble/setup.bash
source ~/ros2_ws/install/setup.bash

nohup ros2 launch astra_camera astra.launch.xml > /tmp/astra.log 2>&1 &
sleep 5
tail -n 80 /tmp/astra.log
```

确认话题：

```bash
ros2 topic list | grep camera
ros2 topic hz /camera/depth/image_raw
ros2 topic echo /camera/depth/camera_info --once
```

看到 `/camera/depth/image_raw`、`/camera/ir/image_raw`、`/camera/depth/points` 就说明摄像头已经在发数据。

## 2. 让摄像头“看到图像”

### 方式 A：用 rqt_image_view 看图

```bash
source /opt/ros/humble/setup.bash
source ~/ros2_ws/install/setup.bash
rqt_image_view
```

打开后选择：

```text
/camera/depth/image_raw
```

或：

```text
/camera/ir/image_raw
```

如果没有 `rqt_image_view`：

```bash
sudo apt install -y ros-humble-rqt-image-view
```

### 方式 B：用 RViz2 看图和点云

```bash
rviz2
```

RViz2 中建议这样设置：

- `Fixed Frame` 先设为 `camera_depth_frame`，如果不存在就用 `camera_link`
- 添加 `Image`，Topic 选 `/camera/depth/image_raw`
- 添加 `PointCloud2`，Topic 选 `/camera/depth/points`

如果 RViz2 中没有画面，先查 frame 名：

```bash
ros2 topic echo /camera/depth/camera_info --once
ros2 run tf2_tools view_frames
```

## 3. 启动 N10P 雷达

确认串口：

```bash
ls /dev/ttyUSB* /dev/ttyACM* 2>/dev/null
ls -l /dev/serial/by-id
```

按前次实测，最终可用串口是：

```text
/dev/ttyACM0
```

启动雷达：

```bash
cd ~/ros2_ws
source /opt/ros/humble/setup.bash
source install/setup.bash

nohup ros2 launch lslidar_driver lslidar_launch.py > /tmp/lslidar.log 2>&1 &
sleep 3
ros2 topic pub -1 /lslidar_order std_msgs/msg/Int8 "{data: 1}"
tail -n 80 /tmp/lslidar.log
```

确认 `/scan`：

```bash
ros2 topic list | grep scan
ros2 topic echo /scan --once
ros2 topic hz /scan
```

## 4. 补最小 TF

如果还没有机器人底盘 TF，先临时发布静态 TF，让 RViz 和 SLAM 能收到基本坐标关系。

雷达安装在机器人中心前方 0.10 m、高 0.12 m，方向与车头一致时：

```bash
ros2 run tf2_ros static_transform_publisher \
  --x 0.10 --y 0 --z 0.12 \
  --roll 0 --pitch 0 --yaw 0 \
  --frame-id base_link \
  --child-frame-id laser_link
```

如果雷达实际安装位置不同，要按实物修改 `x/y/z/yaw`。

检查 TF：

```bash
ros2 run tf2_ros tf2_echo base_link laser_link
```

## 5. 准备 odom -> base_link

`slam_toolbox` 通常需要 `odom -> base_link`。正确方案是由底盘里程计发布这条 TF。

如果底盘里程计还没接入，只是想先验证建图链路，可以临时发布一个静态的 `odom -> base_link`：

```bash
ros2 run tf2_ros static_transform_publisher \
  --x 0 --y 0 --z 0 \
  --roll 0 --pitch 0 --yaw 0 \
  --frame-id odom \
  --child-frame-id base_link
```

注意：这个临时写法只能用于原地验证话题和 TF 是否打通，机器人移动后地图质量会很差。真正建图需要底盘里程计，或接入激光里程计。

## 6. 安装并启动 slam_toolbox

安装：

```bash
sudo apt update
sudo apt install -y ros-humble-slam-toolbox
```

启动在线建图：

```bash
source /opt/ros/humble/setup.bash
source ~/ros2_ws/install/setup.bash

ros2 launch slam_toolbox online_async_launch.py \
  slam_params_file:=/tmp/slam_toolbox_mapper_params.yaml
```

如果没有参数文件，先创建：

```bash
cat > /tmp/slam_toolbox_mapper_params.yaml <<'EOF'
slam_toolbox:
  ros__parameters:
    use_sim_time: false
    odom_frame: odom
    map_frame: map
    base_frame: base_link
    scan_topic: /scan
    mode: mapping
    resolution: 0.05
    max_laser_range: 12.0
    minimum_time_interval: 0.2
    transform_timeout: 0.2
    tf_buffer_duration: 30.0
EOF
```

然后再启动：

```bash
ros2 launch slam_toolbox online_async_launch.py \
  slam_params_file:=/tmp/slam_toolbox_mapper_params.yaml
```

## 7. RViz2 查看建图

```bash
rviz2
```

RViz2 中设置：

- `Fixed Frame` 设为 `map`
- 添加 `Map`，Topic 选 `/map`
- 添加 `LaserScan`，Topic 选 `/scan`
- 添加 `TF`
- 可选添加摄像头：
  - `Image`：`/camera/depth/image_raw`
  - `PointCloud2`：`/camera/depth/points`

如果 `/map` 没有更新，先查：

```bash
ros2 topic hz /scan
ros2 topic echo /scan --once
ros2 topic echo /map --once
ros2 run tf2_ros tf2_echo base_link laser_link
ros2 run tf2_ros tf2_echo odom base_link
```

## 8. 保存地图

建图完成后：

```bash
sudo apt install -y ros-humble-nav2-map-server
mkdir -p ~/maps
ros2 run nav2_map_server map_saver_cli -f ~/maps/home_map
```

保存后应生成：

```text
~/maps/home_map.yaml
~/maps/home_map.pgm
```

## 9. 一键联调顺序

建议按这个顺序开多个终端：

### 终端 1：摄像头

```bash
source /opt/ros/humble/setup.bash
source ~/ros2_ws/install/setup.bash
ros2 launch astra_camera astra.launch.xml
```

### 终端 2：雷达

```bash
cd ~/ros2_ws
source /opt/ros/humble/setup.bash
source install/setup.bash
ros2 launch lslidar_driver lslidar_launch.py
```

另开一次启动雷达：

```bash
ros2 topic pub -1 /lslidar_order std_msgs/msg/Int8 "{data: 1}"
```

### 终端 3：TF

```bash
source /opt/ros/humble/setup.bash
ros2 run tf2_ros static_transform_publisher --x 0.10 --y 0 --z 0.12 --roll 0 --pitch 0 --yaw 0 --frame-id base_link --child-frame-id laser_link
```

如果没有底盘 odom，临时再开：

```bash
source /opt/ros/humble/setup.bash
ros2 run tf2_ros static_transform_publisher --x 0 --y 0 --z 0 --roll 0 --pitch 0 --yaw 0 --frame-id odom --child-frame-id base_link
```

### 终端 4：SLAM

```bash
source /opt/ros/humble/setup.bash
source ~/ros2_ws/install/setup.bash
ros2 launch slam_toolbox online_async_launch.py slam_params_file:=/tmp/slam_toolbox_mapper_params.yaml
```

### 终端 5：可视化

```bash
source /opt/ros/humble/setup.bash
source ~/ros2_ws/install/setup.bash
rviz2
```

## 10. 快速判断问题在哪

### 摄像头没图

```bash
ros2 topic list | grep camera
ros2 topic hz /camera/depth/image_raw
tail -n 100 /tmp/astra.log
```

有 `/camera/depth/image_raw` 且频率正常，说明摄像头没问题，是显示工具或 RViz frame 设置问题。

### 雷达没数据

```bash
ros2 topic list | grep scan
ros2 topic echo /scan --once
tail -n 100 /tmp/lslidar.log
```

没有 `/scan` 时，优先检查 `lsx10.yaml`：

```yaml
lidar_name: N10_P
interface_selection: serial
serial_port_: /dev/ttyACM0
pubScan: true
scan_topic: /scan
```

### SLAM 不出地图

```bash
ros2 topic hz /scan
ros2 run tf2_ros tf2_echo base_link laser_link
ros2 run tf2_ros tf2_echo odom base_link
ros2 topic echo /map --once
```

最常见原因：

- `/scan` 没有数据
- `scan.header.frame_id` 不是 `laser_link`
- 缺少 `base_link -> laser_link`
- 缺少 `odom -> base_link`
- RViz `Fixed Frame` 没设成 `map`

## 11. 下一步建议

真正要让机器人边走边稳定建图，下一步不要停在静态 TF 临时方案，需要补齐：

- 底盘里程计 `/odom`
- `odom -> base_link` 动态 TF
- `base_link -> laser_link` 实测安装外参
- `base_link -> camera_link` 摄像头安装外参
- 后续再接 `nav2` 做定位和导航
