from setuptools import find_packages, setup

package_name = 'ppdv_comm'

setup(
    name=package_name,
    version='1.3.0',
    packages=find_packages(exclude=['test']),
    data_files=[
        ('share/ament_index/resource_index/packages',
            ['resource/' + package_name]),
        ('share/' + package_name, ['package.xml']),
        ('share/' + package_name + '/config', ['config/comm.yaml']),
        ('share/' + package_name + '/launch', ['launch/comm.launch.py']),
    ],
    install_requires=['setuptools'],
    zip_safe=True,
    maintainer='PPDV',
    maintainer_email='ppdv@example.com',
    description='PPDV 车载端 MQTT 通信上报节点（遥测上行/跟踪观测上行/指令下行/断网补传）',
    license='MIT',
    tests_require=['pytest'],
    entry_points={
        'console_scripts': [
            'comm_node = ppdv_comm.comm_node:main',
        ],
    },
)
