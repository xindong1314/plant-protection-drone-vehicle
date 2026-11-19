"""单测公共桩：假时钟、假 MQTT sender、同步执行器、捕获发布。"""
import sys
from pathlib import Path

# 保证从任意工作目录运行 pytest 都能 import ppdv_comm
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class FakeClock:
    """可手动推进的时钟（epoch ms）。"""

    def __init__(self, start_ms=1_700_000_000_000):
        self.now_ms = start_ms

    def __call__(self) -> int:
        return self.now_ms

    def advance_ms(self, ms):
        self.now_ms += ms


class RecordingSender:
    """假 MQTT 发送端：记录 publish 并可控制在线状态与失败。"""

    def __init__(self, connected=True, fail_topics=()):
        self.connected = connected
        self.fail_topics = frozenset(fail_topics)
        self.published = []  # (topic, payload, qos, retain)

    def is_connected(self):
        return self.connected

    def publish(self, topic, payload, qos=0, retain=False):
        if topic in self.fail_topics:
            return False
        self.published.append((topic, payload, qos, retain))
        return True

    def set_connected(self, connected):
        self.connected = connected

    def payloads_of(self, kind_suffix):
        return [p for (t, p, *_ ) in self.published if t.endswith(kind_suffix)]

    def clear(self):
        self.published.clear()


class SyncExecutor:
    """同步执行器：submit 立即执行（单测不引入线程时序）。"""

    def submit(self, fn, *args):
        fn(*args)
