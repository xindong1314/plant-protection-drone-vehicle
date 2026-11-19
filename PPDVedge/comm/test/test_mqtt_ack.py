"""paho publish_wait_ack 纯桩测试：不依赖 broker，覆盖成功/超时/rc!=0/属性语义。"""
from ppdv_comm.mqtt_link import EdgeMqttClient


class FakeInfo:
    def __init__(self, rc=0, published=True):
        self.rc = rc
        self._published = published
        self.waits = []

    def wait_for_publish(self, timeout=None):
        self.waits.append(timeout)

    def is_published(self):
        return self._published


class PropertyInfo(FakeInfo):
    """模拟未来 paho 把 is_published 改成属性（不是方法）。"""

    @property
    def is_published(self):
        return self._published


class FakeClient:
    def __init__(self, info):
        self.info = info

    def publish(self, topic, payload, qos=0, retain=False):
        return self.info


def make_client(info):
    client = object.__new__(EdgeMqttClient)
    client.client = FakeClient(info)
    client._log = lambda message: None
    return client


def test_publish_wait_ack_success():
    info = FakeInfo()
    client = make_client(info)
    assert client.publish_wait_ack("t", "p", qos=1, timeout_s=2.5) is True
    assert info.waits == [2.5]


def test_publish_wait_ack_timeout_returns_false():
    info = FakeInfo(published=False)
    client = make_client(info)
    assert client.publish_wait_ack("t", "p", qos=1, timeout_s=0.1) is False
    assert info.waits == [0.1]


def test_publish_wait_ack_rc_error_does_not_wait():
    info = FakeInfo(rc=2, published=True)
    client = make_client(info)
    assert client.publish_wait_ack("t", "p", qos=1) is False
    assert info.waits == []


def test_publish_wait_ack_accepts_property_is_published():
    info = PropertyInfo()
    client = make_client(info)
    assert client.publish_wait_ack("t", "p", qos=1) is True


def test_publish_wait_ack_exception_returns_false():
    class BoomClient:
        def publish(self, *args, **kwargs):
            raise OSError("boom")

    client = object.__new__(EdgeMqttClient)
    client.client = BoomClient()
    client._log = lambda message: None
    assert client.publish_wait_ack("t", "p", qos=1) is False
