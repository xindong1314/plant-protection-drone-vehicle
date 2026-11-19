import os

from ament_index_python.packages import get_package_share_directory
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, OpaqueFunction
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node


def _launch_setup(context, *args, **kwargs):
    params_file = LaunchConfiguration('params_file').perform(context)
    source = LaunchConfiguration('source').perform(context)
    device_id = LaunchConfiguration('device_id').perform(context)
    sim_base_url = LaunchConfiguration('sim_base_url').perform(context)
    log_level = LaunchConfiguration('log_level').perform(context)

    # 凭据用环境变量注入（绝不写入文件）：PPDV_DEVICE_ID / PPDV_DEVICE_SECRET
    parameters = []
    if params_file:
        parameters.append(params_file)
    overrides = {}
    if source:
        overrides['source'] = source
    if device_id:
        overrides['device_id'] = device_id
    if sim_base_url:
        overrides['sim_base_url'] = sim_base_url
    if overrides:
        parameters.append(overrides)

    return [
        Node(
            package='ppdv_comm',
            executable='comm_node',
            name='ppdv_comm_node',
            output='screen',
            arguments=['--ros-args', '--log-level', f'ppdv_comm:={log_level or "info"}'],
            parameters=parameters,
        ),
    ]


def generate_launch_description():
    default_params = os.path.join(get_package_share_directory('ppdv_comm'),
                                  'config', 'comm.yaml')
    return LaunchDescription([
        DeclareLaunchArgument('params_file', default_value=default_params),
        DeclareLaunchArgument(
            'device_id', default_value='',
            description='设备编号（空则用 params_file 或环境变量 PPDV_DEVICE_ID）'),
        DeclareLaunchArgument(
            'source', default_value='sim',
            description='数据源：sim=小车模拟器 REST | ros=真车 ROS2 话题'),
        DeclareLaunchArgument(
            'sim_base_url', default_value='http://127.0.0.1:8090',
            description='模拟器地址（source=sim 时生效）'),
        DeclareLaunchArgument(
            'log_level', default_value='info'),
        OpaqueFunction(function=_launch_setup),
    ])
