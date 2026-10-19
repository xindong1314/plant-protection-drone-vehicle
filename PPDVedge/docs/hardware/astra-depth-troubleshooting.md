# Astra Depth Camera ROS2 Runbook

本文记录在 `davinci-mini` 上排查 Orbbec Astra 深度相机 ROS2 topic 卡住的问题，以及最终可用的启动方式。

## 当前结论

相机硬件和 ROS2 节点都可以识别，但默认或错误分辨率会导致 depth stream 没有真正启动。

已确认可用输出：

```bash
/camera/depth/camera_info
/camera/depth/image_raw
```

已确认实际出帧：

```text
/camera/depth/image_raw: about 15 Hz
/camera/depth/camera_info: 320x200
```

## 正确启动命令

推荐稳定模式：

```bash
conda deactivate || true
ros2 launch astra_camera astra.launch.xml enable_depth:=true enable_ir:=false enable_point_cloud:=false depth_width:=320 depth_height:=200 depth_fps:=15
```

更高分辨率模式：

```bash
conda deactivate || true
ros2 launch astra_camera astra.launch.xml enable_depth:=true enable_ir:=false enable_point_cloud:=false depth_width:=640 depth_height:=400 depth_fps:=30
```

不要使用这些 depth 模式：

```text
320x240
640x480
```

这台 Astra depth stream 不支持这些分辨率，启动日志会出现 `Stream will be disabled`，随后 topic 虽然存在但没有帧。

## 验证命令

启动相机后，另开一个终端执行：

```bash
conda deactivate || true
ros2 topic list | grep camera
ros2 topic echo /camera/depth/camera_info --once
ros2 topic hz /camera/depth/image_raw
```

正常时 `ros2 topic hz` 会看到类似：

```text
average rate: 15.0
```

刚开始可能出现：

```text
WARNING: topic [/camera/depth/image_raw] does not appear to be published yet
```

只要后面继续输出 `average rate`，就说明相机正在发布图像。

## 已遇到的问题和原因

### 1. topic 存在但 echo/hz 卡住

现象：

```bash
ros2 topic list | grep camera
ros2 topic echo /camera/depth/camera_info --once
ros2 topic hz /camera/depth/image_raw
```

`topic list` 能看到 topic，但 `echo` 或 `hz` 没有数据。

原因：

相机节点注册了 publisher，但底层 depth stream 没有成功启动。

### 2. Resource busy

启动日志：

```text
Could not open "2bc5/0614@3/8": Resource busy!
```

原因：

旧的 `astra_camera_node` 或其他进程占用了设备。

处理：

```bash
pkill -f astra_camera_node
pkill -f "ros2 launch astra_camera"
sleep 2
ps aux | grep -E 'astra|orbbec|camera|openni|uvc' | grep -v grep
ros2 node list
```

确认没有残留节点后重新启动。

### 3. 不支持的 depth video mode

错误启动示例：

```bash
ros2 launch astra_camera astra.launch.xml enable_depth:=true enable_ir:=false enable_point_cloud:=false depth_width:=320 depth_height:=240 depth_fps:=15
```

日志：

```text
Video mode Resolution :320x240@15Hz
format PIXEL_FORMAT_DEPTH_1_MM is not supported.
Stream will be disabled.
```

处理：

改用支持的模式。

## 已确认支持的 depth 模式

从启动日志确认支持：

```text
320x200 @ 15/30/60
640x400 @ 15/30/60
1280x800 @ 15/30
```

## 设备信息

```text
Device: Astra
serial_number: AY2755200LC
USB ID: 2bc5:0614
```

曾用命令：

```bash
lsusb
ros2 service call /camera/get_device_info astra_camera_msgs/srv/GetDeviceInfo
ros2 service call /camera/get_depth_supported_video_modes astra_camera_msgs/srv/GetString
```

## 如果需要点云

重新启动时打开 point cloud：

```bash
ros2 launch astra_camera astra.launch.xml enable_depth:=true enable_ir:=false enable_point_cloud:=true depth_width:=320 depth_height:=200 depth_fps:=15
```

验证：

```bash
ros2 topic list | grep points
ros2 topic hz /camera/depth/points
```

## 显示深度图和 SLAM 可视化记录

### 当前状态

深度图已经确认正常发布：

```bash
ros2 topic echo /camera/depth/camera_info --once
ros2 topic hz /camera/depth/image_raw
```

已看到 `/camera/depth/image_raw` 约 `15 Hz`。

### 板子 VNC 显示

板子上 `rviz2` 在当前 VNC/X11 环境中不可用，会报 OpenGL/GLX 渲染窗口错误：

```text
RenderingAPIException: Invalid parentWindowHandle
Unable to create the rendering window after 100 tries
```

所以不要优先在板子 VNC 中跑 RViz2。

`rqt_image_view` 已安装，但不能直接用 `rqt_image_view` 命令启动。正确方式：

```bash
source /opt/ros/humble/setup.bash
source ~/ros2_ws/install/setup.bash
export DISPLAY=:1
export QT_X11_NO_MITSHM=1
ros2 run rqt_image_view rqt_image_view
```

启动后可能打印：

```text
XKeyboard extension not present on the X server
```

这通常不是致命错误。窗口会出现在板子的 VNC 桌面里，不会出现在电脑本地桌面。

曾经查到窗口存在但较小：

```bash
xwininfo -root -tree | grep -i rqt
```

输出中真实窗口类似：

```text
rqt_image_view__ImageView - rqt
```

在窗口里选择：

```text
/camera/depth/image_raw
```

### 电脑本地显示建议

长期开发和 SLAM 调试不建议依赖板子 VNC。推荐：

- 板子运行相机、雷达、里程计、SLAM 节点
- 电脑本地运行 `rviz2` 或 `rqt_image_view`

电脑本地显示的前提：

- 电脑有 Ubuntu 22.04 + ROS2 Humble
- 电脑和板子在同一局域网
- 两边 `ROS_DOMAIN_ID` 一致
- 两边 `ROS_LOCALHOST_ONLY=0`

板子端：

```bash
source /opt/ros/humble/setup.bash
source ~/ros2_ws/install/setup.bash
export ROS_DOMAIN_ID=0
export ROS_LOCALHOST_ONLY=0
ros2 launch astra_camera astra.launch.xml enable_depth:=true enable_ir:=false enable_point_cloud:=true depth_width:=320 depth_height:=200 depth_fps:=15
```

电脑端：

```bash
source /opt/ros/humble/setup.bash
export ROS_DOMAIN_ID=0
export ROS_LOCALHOST_ONLY=0
ros2 topic list | grep camera
ros2 run rqt_image_view rqt_image_view
```

如果电脑能看到：

```text
/camera/depth/image_raw
/camera/depth/camera_info
```

就可以在电脑上选择 `/camera/depth/image_raw` 查看深度图。

### SLAM 下一步

2D SLAM 建图优先使用激光雷达 `/scan`，深度相机主要作为辅助感知。下次继续时先检查：

```bash
ros2 topic list | grep -E "scan|odom|tf|camera"
```

如果有 `/scan`、`/odom` 和完整 TF，再接 `slam_toolbox`。如果没有 `/scan`，需要先启动 N10P 雷达，或临时用 `depthimage_to_laserscan` 从深度图转换出 scan。
