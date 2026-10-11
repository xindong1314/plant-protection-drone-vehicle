# ROS2 Colcon 编译器安装指南 - Atlas 200I DK A2

## 系统信息
- 设备：Atlas 200I DK A2
- 系统：Ubuntu 22.04 LTS (ARM64/aarch64)
- ROS版本：ROS2 Humble Desktop
- Colcon版本：0.3.0-100

## 前置条件
- 已安装 ROS2 Humble（参考 [ROS2 Humble 安装指南](ros2-humble-install.md)）
- 已配置 ROS2 环境变量

## 安装步骤

### 1. 安装 colcon 及扩展
```bash
# 更新软件包列表
sudo apt update

# 安装 colcon 核心和常用扩展
sudo apt install -y python3-colcon-common-extensions

# 安装额外工具（可选）
sudo apt install -y python3-colcon-mixin python3-vcstool
```

### 2. 配置自动补全
```bash
# 添加 colcon 命令自动补全到 bashrc
echo "source /usr/share/colcon_argcomplete/hook/colcon-argcomplete.bash" >> ~/.bashrc

# 立即加载配置
source ~/.bashrc
```

### 3. 验证安装
```bash
# 检查 colcon 版本
colcon version-check

# 查看帮助信息
colcon --help

# 查看可用命令
colcon list -h
```

## 创建 ROS2 工作空间

### 1. 创建工作空间目录
```bash
# 创建工作空间
mkdir -p ~/ros2_ws/src
cd ~/ros2_ws
```

### 2. 测试编译
```bash
# 编译空工作空间（验证 colcon 是否正常）
colcon build

# 成功后会生成三个目录：
# - build/   : 编译过程文件
# - install/ : 安装的包
# - log/     : 编译日志
```

### 3. 加载工作空间环境
```bash
# 加载工作空间
source install/setup.bash

# 或添加到 bashrc（每次自动加载）
echo "source ~/ros2_ws/install/setup.bash" >> ~/.bashrc
```

## 常用 colcon 命令

### 基本编译命令
```bash
# 编译所有包
colcon build

# 编译指定包
colcon build --packages-select <package_name>

# 编译多个指定包
colcon build --packages-select pkg1 pkg2 pkg3

# 跳过某个包
colcon build --packages-skip <package_name>
```

### 编译选项
```bash
# 符号链接安装（Python 包推荐，修改代码无需重新编译）
colcon build --symlink-install

# 并行编译（加速编译，根据 CPU 核心数调整）
colcon build --parallel-workers 4

# 继续编译（跳过已成功的包）
colcon build --continue-on-error

# 详细输出（调试用）
colcon build --event-handlers console_direct+
```

### 清理命令
```bash
# 清理所有编译结果
rm -rf build install log

# 或只清理特定目录
rm -rf build
```

### 测试命令
```bash
# 运行所有测试
colcon test

# 运行指定包的测试
colcon test --packages-select <package_name>

# 查看测试结果
colcon test-result --all
colcon test-result --verbose
```

## 创建 ROS2 包示例

### 创建 Python 包
```bash
cd ~/ros2_ws/src

# 创建 Python 包
ros2 pkg create --build-type ament_python my_py_pkg \
  --dependencies rclpy

# 编译
cd ~/ros2_ws
colcon build --packages-select my_py_pkg --symlink-install

# 加载环境
source install/setup.bash
```

### 创建 C++ 包
```bash
cd ~/ros2_ws/src

# 创建 C++ 包
ros2 pkg create --build-type ament_cmake my_cpp_pkg \
  --dependencies rclcpp

# 编译
cd ~/ros2_ws
colcon build --packages-select my_cpp_pkg

# 加载环境
source install/setup.bash
```

## 工作流程示例

### 典型开发流程
```bash
# 1. 进入工作空间
cd ~/ros2_ws

# 2. 创建或修改包
cd src
# ... 编辑代码 ...

# 3. 返回工作空间根目录
cd ~/ros2_ws

# 4. 编译（Python 包用 --symlink-install）
colcon build --symlink-install

# 5. 加载环境
source install/setup.bash

# 6. 运行节点
ros2 run <package_name> <node_name>
```

## 常见问题

### 1. 编译失败
```bash
# 查看详细错误信息
colcon build --event-handlers console_direct+

# 查看日志文件
cat log/latest_build/<package_name>/stdout.log
cat log/latest_build/<package_name>/stderr.log
```

### 2. 找不到包
```bash
# 确保已加载工作空间环境
source ~/ros2_ws/install/setup.bash

# 检查包是否在工作空间中
colcon list
```

### 3. Python 包修改后不生效
```bash
# 使用 --symlink-install 重新编译
colcon build --symlink-install

# 或清理后重新编译
rm -rf build install log
colcon build --symlink-install
```

### 4. 依赖问题
```bash
# 安装 rosdep（如果未安装）
sudo apt install -y python3-rosdep

# 初始化 rosdep
sudo rosdep init
rosdep update

# 安装工作空间所有依赖
cd ~/ros2_ws
rosdep install --from-paths src --ignore-src -r -y
```

## 性能优化建议

### 1. 并行编译
```bash
# 根据 CPU 核心数设置（Atlas 200I DK A2 建议 2-4）
colcon build --parallel-workers 4
```

### 2. 增量编译
```bash
# 只编译修改过的包
colcon build --packages-up-to <package_name>
```

### 3. 使用 ccache（C++ 编译加速）
```bash
# 安装 ccache
sudo apt install -y ccache

# 配置环境变量
export CC="ccache gcc"
export CXX="ccache g++"
```

## 环境变量配置

### 推荐的 ~/.bashrc 配置
```bash
# ROS2 环境
source /opt/ros/humble/setup.bash

# Colcon 自动补全
source /usr/share/colcon_argcomplete/hook/colcon-argcomplete.bash

# 工作空间环境（可选，根据需要启用）
# source ~/ros2_ws/install/setup.bash

# Colcon 编译选项（可选）
export COLCON_DEFAULTS_FILE=~/.colcon/defaults.yaml
```

## 高级配置

### 创建 colcon 默认配置文件
```bash
# 创建配置目录
mkdir -p ~/.colcon

# 创建默认配置
cat > ~/.colcon/defaults.yaml << 'EOF'
build:
  symlink-install: true
  parallel-workers: 4
  event-handlers:
    - console_cohesion+
test:
  parallel-workers: 4
EOF
```

配置后，运行 `colcon build` 会自动应用这些选项。

## 注意事项
- Python 包开发建议使用 `--symlink-install`，修改代码后无需重新编译
- C++ 包修改后必须重新编译
- 每次打开新终端都需要 `source install/setup.bash`
- 建议将常用的 source 命令添加到 `~/.bashrc`
- 编译大型项目时注意磁盘空间（build 目录可能很大）

## 参考资源
- [Colcon 官方文档](https://colcon.readthedocs.io/)
- [ROS2 构建系统教程](https://docs.ros.org/en/humble/Tutorials/Beginner-Client-Libraries/Colcon-Tutorial.html)

---
安装日期：2026-04-19
