# ROS2 Humble 安装指南 - Atlas 200I DK A2

## 系统信息
- 设备：Atlas 200I DK A2
- 系统：Ubuntu 22.04 LTS (ARM64/aarch64)
- ROS版本：ROS2 Humble Desktop
- 网络环境：中国（使用清华镜像源）

## 安装步骤

### 1. 设置 Locale
```bash
sudo apt update && sudo apt install -y locales
sudo locale-gen en_US.UTF-8
sudo update-locale LC_ALL=en_US.UTF-8 LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
export LANG=en_US.UTF-8
```

### 2. 安装必要工具
```bash
sudo apt install -y software-properties-common curl gnupg lsb-release
sudo add-apt-repository universe -y
```

### 3. 添加 ROS2 镜像源（清华源）
```bash
# 下载 GPG 密钥
sudo curl -sSL https://raw.githubusercontent.com/ros/rosdistro/master/ros.key -o /usr/share/keyrings/ros-archive-keyring.gpg

# 设置架构和代号变量
ARCH=$(dpkg --print-architecture)
CODENAME=$(source /etc/os-release && echo $UBUNTU_CODENAME)

# 添加 ROS2 仓库
echo "deb [arch=$ARCH signed-by=/usr/share/keyrings/ros-archive-keyring.gpg] https://mirrors.tuna.tsinghua.edu.cn/ros2/ubuntu $CODENAME main" | sudo tee /etc/apt/sources.list.d/ros2.list

# 更新软件包列表
sudo apt update
```

### 4. 修复依赖问题（如遇到）
```bash
# 如果安装 ros-humble-desktop 时提示依赖错误，先安装：
sudo apt install -y libegl-mesa0
```

### 5. 安装 ROS2 Humble
```bash
# 桌面版（包含 RViz、rqt 等工具）
sudo apt install -y ros-humble-desktop

# 或精简版（仅核心功能）
# sudo apt install -y ros-humble-ros-base
```

### 6. 配置环境变量
```bash
# 添加到 bashrc（每次打开终端自动加载）
echo "source /opt/ros/humble/setup.bash" >> ~/.bashrc

# 立即加载环境
source /opt/ros/humble/setup.bash
```

### 7. 验证安装
```bash
# 查看话题列表
ros2 topic list

# 应该看到：
# /parameter_events
# /rosout
```

## 可选：安装开发工具
```bash
sudo apt install -y python3-colcon-common-extensions python3-rosdep
```

## 测试示例
```bash
# 终端1：运行发布者
ros2 run demo_nodes_cpp talker

# 终端2：运行订阅者
ros2 run demo_nodes_cpp listener
```

## 常见问题

### 1. GPG 密钥错误
如果遇到 `NO_PUBKEY` 错误，重新下载密钥：
```bash
sudo rm -f /usr/share/keyrings/ros-archive-keyring.gpg
sudo curl -sSL https://raw.githubusercontent.com/ros/rosdistro/master/ros.key -o /usr/share/keyrings/ros-archive-keyring.gpg
sudo apt update
```

### 2. 依赖冲突
先更新 Mesa 库：
```bash
sudo apt install -y libegl-mesa0
```

### 3. 服务重启提示
安装过程中如果提示重启服务，可以直接按回车跳过，稍后重启系统即可。

## 其他镜像源

如果清华源速度不理想，可替换为：

**中科大镜像：**
```
https://mirrors.ustc.edu.cn/ros2/ubuntu
```

**阿里云镜像：**
```
https://mirrors.aliyun.com/ros2/ubuntu
```

## 注意事项
- 安装过程中可能需要下载 1-2GB 数据
- 可以随时 Ctrl+C 中断，下次继续安装
- 建议在网络稳定时进行安装
- 安装完成后建议重启系统以确保所有服务正常

---
安装日期：2026-04-14
