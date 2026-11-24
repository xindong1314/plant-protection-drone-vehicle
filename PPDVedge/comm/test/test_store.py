"""OfflineCache：JSONL 追加/按序读出/坏行跳过/清空。"""
import json


def test_append_read_clear_roundtrip(tmp_path):
    from ppdv_comm.store import OfflineCache
    cache = OfflineCache(str(tmp_path / "cache"), "PPDV-0001")
    assert cache.append("ppdv/v1/PPDV-0001/tele/state", '{"a": 1, "msg": "中文"}') is True
    assert cache.append("ppdv/v1/PPDV-0001/tele/work", '{"b": 2}') is True
    entries = cache.read_all()
    assert [t for (t, _p) in entries] == ["ppdv/v1/PPDV-0001/tele/state",
                                          "ppdv/v1/PPDV-0001/tele/work"]
    assert json.loads(entries[0][1])["msg"] == "中文"
    assert cache.size() == 2
    cache.clear()
    assert cache.size() == 0 and cache.read_all() == []


def test_bad_lines_skipped(tmp_path):
    from ppdv_comm.store import OfflineCache
    cache = OfflineCache(str(tmp_path / "cache"), "PPDV-0001")
    cache.append("t/opic", '{"x": 1}')
    with open(cache.path, "a", encoding="utf-8") as f:
        f.write("{broken json\n")          # 坏行
        f.write('{"no_payload": 1}\n')     # 缺 payload
        f.write("\n")                      # 空行
    cache.append("t/opic2", '{"x": 2}')
    entries = cache.read_all()
    assert len(entries) == 2
    assert entries[1][1] == '{"x": 2}'


def test_per_device_file(tmp_path):
    from ppdv_comm.store import OfflineCache
    a = OfflineCache(str(tmp_path / "cache"), "A")
    b = OfflineCache(str(tmp_path / "cache"), "B")
    a.append("t", '{"a": 1}')
    assert a.size() == 1 and b.size() == 0
    assert a.path.name == "A.jsonl" and b.path.name == "B.jsonl"


def test_missing_file_reads_empty(tmp_path):
    from ppdv_comm.store import OfflineCache
    cache = OfflineCache(str(tmp_path / "no-such-dir" / "cache"), "X")
    assert cache.read_all() == []


# ---- tele/track：按会话合并 ----

TOPIC = "ppdv/v1/PPDV-0001/tele/track"


def _track_payload(session, window, end=False):
    return json.dumps({"msgId": "m", "deviceId": "PPDV-0001", "ts": 1, "type": "track",
                       "data": {"trackSessionId": session, "sessionEnd": end,
                                "windowIndex": window, "tracks": []}})


def _cache(tmp_path):
    from ppdv_comm.store import OfflineCache
    return OfflineCache(str(tmp_path / "cache"), "PPDV-0001")


def test_prune_track_windows_by_session_only(tmp_path):
    cache = _cache(tmp_path)
    cache.append(TOPIC, _track_payload("TS-1", 0))
    cache.append(TOPIC, _track_payload("TS-1", 1))
    cache.append(TOPIC, _track_payload("TS-1", 9, end=True))
    cache.append(TOPIC, _track_payload("TS-OTHER", 0))
    cache.append("ppdv/v1/PPDV-0001/tele/work", '{"flow": 1.0}')

    assert cache.has_stale_track_windows(TOPIC, "TS-1", 2) is True
    assert cache.prune_track_windows(TOPIC, "TS-1", 2) == 2

    entries = cache.read_all()
    assert len(entries) == 3
    sessions = []
    for topic, payload in entries:
        if topic == TOPIC:
            data = json.loads(payload)["data"]
            sessions.append((data["trackSessionId"], data["windowIndex"]))
        else:
            sessions.append(("work", None))
    assert sessions == [("TS-1", 9), ("TS-OTHER", 0), ("work", None)]
    assert cache.has_stale_track_windows(TOPIC, "TS-1", 2) is False


def test_prune_keeps_same_window_shards(tmp_path):
    cache = _cache(tmp_path)
    cache.append(TOPIC, _track_payload("TS-1", 3))
    cache.append(TOPIC, _track_payload("TS-1", 3))
    assert cache.prune_track_windows(TOPIC, "TS-1", 3) == 0
    assert cache.size() == 2


def test_prune_treats_missing_window_index_as_older(tmp_path):
    cache = _cache(tmp_path)
    legacy = json.dumps({"msgId": "m", "deviceId": "PPDV-0001", "ts": 1, "type": "track",
                         "data": {"trackSessionId": "TS-1", "sessionEnd": False,
                                  "tracks": []}})
    cache.append(TOPIC, legacy)
    assert cache.prune_track_windows(TOPIC, "TS-1", 1) == 1
    assert cache.size() == 0


def test_prune_keeps_unparseable_and_foreign_payloads(tmp_path):
    cache = _cache(tmp_path)
    cache.append(TOPIC, "not-a-json-payload")
    cache.append(TOPIC, json.dumps({"type": "state", "data": {"lat": 1.0}}))
    cache.append(TOPIC, _track_payload("TS-1", 0))
    assert cache.prune_track_windows(TOPIC, "TS-1", 1) == 1
    assert cache.size() == 2                    # 坏 payload 与异类 payload 原样保留

    with open(cache.path, encoding="utf-8") as handle:   # 重写后仍是合法 JSONL
        lines = [line for line in handle if line.strip()]
    assert len(lines) == 2
    assert all(json.loads(line)["topic"] for line in lines)


def test_remove_entry_only_removes_exact_match(tmp_path):
    cache = _cache(tmp_path)
    first, second = _track_payload("TS-1", 1), _track_payload("TS-1", 2)
    cache.append(TOPIC, first)
    cache.append(TOPIC, second)
    assert cache.remove_entry(TOPIC, first) is True
    assert cache.remove_entry(TOPIC, first) is False
    entries = cache.read_all()
    assert [payload for _topic, payload in entries] == [second]


def test_prune_leaves_no_temp_file(tmp_path):
    cache = _cache(tmp_path)
    cache.append(TOPIC, _track_payload("TS-1", 0))
    cache.prune_track_windows(TOPIC, "TS-1", 1)
    assert list(cache.path.parent.glob("*.tmp")) == []

