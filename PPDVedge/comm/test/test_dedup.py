"""512 滑窗去重：淘汰边界（窗口满后最旧 id 出窗、出窗 id 的重复包能被正常处理）。"""
from ppdv_comm.mqtt_link import DEDUP_CAPACITY, MsgIdDeduper


def test_first_seen_not_duplicate():
    d = MsgIdDeduper()
    assert d.is_duplicate_and_track("a") is False


def test_repeat_within_window_is_duplicate():
    d = MsgIdDeduper()
    d.is_duplicate_and_track("a")
    assert d.is_duplicate_and_track("a") is True


def test_duplicate_does_not_change_window_state():
    d = MsgIdDeduper(capacity=3)
    for mid in ("a", "b"):
        d.is_duplicate_and_track(mid)
    assert d.is_duplicate_and_track("a") is True  # 重复包不推进窗口
    assert len(d) == 2


def test_capacity_eviction_boundary():
    """窗口满 512 后：新 id 入窗，最旧 id 出窗；重复包不推进窗口。"""
    d = MsgIdDeduper()
    ids = [f"m{i}" for i in range(DEDUP_CAPACITY)]
    for mid in ids:
        assert d.is_duplicate_and_track(mid) is False
    assert len(d) == DEDUP_CAPACITY
    # 窗口内全部判重，且重复包不推进窗口
    for mid in ids:
        assert d.is_duplicate_and_track(mid) is True
    assert len(d) == DEDUP_CAPACITY
    # 第 513 条：最旧 m0 出窗
    assert d.is_duplicate_and_track("new") is False
    assert len(d) == DEDUP_CAPACITY
    # m0 已出窗：视为新消息，重新入窗（同时 m1 被挤出）
    assert d.is_duplicate_and_track("m0") is False
    assert d.is_duplicate_and_track("m1") is False  # m1 已出窗，重入再挤 m2
    assert d.is_duplicate_and_track("m3") is True   # m3 仍在窗内
    assert d.is_duplicate_and_track("m0") is True   # m0 刚重新入窗，判重


def test_evicted_id_repeat_processed_normally():
    """出窗 id 的重复包被正常处理（重新入窗、窗口如常滚动）。"""
    d = MsgIdDeduper(capacity=4)
    for mid in ("a", "b", "c", "d"):
        d.is_duplicate_and_track(mid)          # [a b c d]
    d.is_duplicate_and_track("e")              # a 出窗 → [b c d e]
    assert d.is_duplicate_and_track("a") is False   # a 重新入窗 → b 出窗 [c d e a]
    assert d.is_duplicate_and_track("b") is False   # b 已出窗 → c 出窗 [d e a b]
    assert d.is_duplicate_and_track("c") is False   # c 已出窗 → d 出窗 [e a b c]
    assert d.is_duplicate_and_track("d") is False   # d 已出窗 → e 出窗 [a b c d]
    assert d.is_duplicate_and_track("a") is True    # 完整滚动一轮后 a 仍在窗内
    assert d.is_duplicate_and_track("e") is False   # e 已被挤出，正常处理


def test_capacity_constant():
    assert DEDUP_CAPACITY == 512
