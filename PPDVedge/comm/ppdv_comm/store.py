"""断网本地缓存：JSONL 逐行追加，恢复后按序补传（ts 保持原采集时间）。

文件 {cache_dir}/{device_id}.jsonl，每行一个 {"topic":...,"payload":"..."}，
坏行跳过。全部公开方法持同一把锁（read_all/size 也在锁内，审核修正：
flush 的 read→clear/重写 与并发 append 不得交错）；IO 异常兜底为记日志
返回空/False，不向上冒泡拖垮遥测轮次。

tele/track 另有"按会话合并"能力：新窗口发送前清理同 topic、同
trackSessionId、sessionEnd 非 true 且 windowIndex 更小的旧条目；同 windowIndex 的
分片、结束条目、其他主题与其他会话一律不动，无法解析的旧行原样保留。清理/重写
一律"临时文件 + 原子替换"，不留下半截文件。
"""
import json
import os
import threading
from pathlib import Path


class OfflineCache:

    def __init__(self, cache_dir: str, device_id: str, log=None):
        self._log = log or (lambda msg: None)
        self.path = Path(cache_dir) / (device_id + ".jsonl")
        try:
            self.path.parent.mkdir(parents=True, exist_ok=True)
        except OSError as e:
            self._log(f"[缓存] 创建缓存目录失败 {self.path.parent}: {e}")
        self._lock = threading.Lock()

    def append(self, topic: str, payload: str) -> bool:
        """离线期间追加一条待补传消息；返回是否落盘成功（失败不抛异常）。"""
        line = json.dumps({"topic": topic, "payload": payload}, ensure_ascii=False)
        with self._lock:
            try:
                with open(self.path, "a", encoding="utf-8") as f:
                    f.write(line + "\n")
                return True
            except OSError as e:
                self._log(f"[缓存] 追加缓存失败 {self.path}: {e}")
                return False

    def read_all(self):
        """按写入顺序读出全部待补传消息，每条为 (topic, payload)；坏行跳过。"""
        with self._lock:
            return self._read_all_locked()

    def _read_all_locked(self):
        if not self.path.exists():
            return []
        entries = []
        try:
            with open(self.path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        e = json.loads(line)
                        if isinstance(e, dict) and e.get("topic") and e.get("payload"):
                            entries.append((str(e["topic"]), str(e["payload"])))
                    except ValueError:
                        continue
        except OSError as e:
            self._log(f"[缓存] 读取缓存失败 {self.path}: {e}")
        return entries

    def clear(self):
        with self._lock:
            self._clear_locked()

    def _clear_locked(self):
        try:
            self.path.unlink(missing_ok=True)
        except OSError as e:
            self._log(f"[缓存] 清理缓存失败 {self.path}: {e}")

    def size(self) -> int:
        with self._lock:
            return len(self._read_all_locked())

    def has_entries(self) -> bool:
        """缓存文件是否存在且非空（廉价判定：空缓存时避免逐行全量扫描）。"""
        with self._lock:
            try:
                return self.path.exists() and self.path.stat().st_size > 0
            except OSError:
                return False

    # ---- tele/track：按会话合并 ----
    # windowIndex 排序/合并只用于本地诊断与乱序排查：云端 TrackIngestService 不读该
    # 字段，乱序/重复由 msgId 去重 + lastSeenTs 单调守卫 + sessionEnd 幂等兜底。
    # 真正有价值的是"按会话合并、缓存不随窗口数线性增长"，不要把它当云端排序依据。

    def has_stale_track_windows(self, topic: str, session_id: str, window_index) -> bool:
        """是否存在同会话更小 windowIndex 的未结束旧窗口（供发送前决定先持久化）。"""
        with self._lock:
            for line in self._read_lines_locked():
                if _is_stale_track_line(line, topic, session_id, window_index):
                    return True
            return False

    def prune_track_windows(self, topic: str, session_id: str, window_index) -> int:
        """原子清理同会话更小 windowIndex 的未结束旧窗口，返回删除条数。"""
        with self._lock:
            lines = self._read_lines_locked()
            if not lines:
                return 0
            kept = [line for line in lines
                    if not _is_stale_track_line(line, topic, session_id, window_index)]
            removed = len(lines) - len(kept)
            if removed:
                self._rewrite_locked(kept)
            return removed

    def remove_entry(self, topic: str, payload: str) -> bool:
        """按 (topic, payload) 精确删除一条条目（在线确认后回收新副本）。"""
        with self._lock:
            lines = self._read_lines_locked()
            for index, line in enumerate(lines):
                entry = _line_entry(line)
                if entry is not None and entry[0] == topic and entry[1] == payload:
                    del lines[index]
                    self._rewrite_locked(lines)
                    return True
            return False

    # ---- 内部（调用方持锁） ----

    def _read_lines_locked(self):
        """按写入顺序读出非空原始行（用于按行重写；坏行原样保留）。"""
        if not self.path.exists():
            return []
        try:
            with open(self.path, "r", encoding="utf-8") as f:
                return [line.strip() for line in f if line.strip()]
        except OSError as e:
            self._log(f"[缓存] 读取缓存失败 {self.path}: {e}")
            return []

    def _rewrite_locked(self, lines):
        """临时文件 + 原子替换（重写中途不留下半截文件）；空列表等价于清空。"""
        if not lines:
            self._clear_locked()
            return
        tmp = self.path.with_name(self.path.name + ".tmp")
        try:
            with open(tmp, "w", encoding="utf-8") as f:
                for line in lines:
                    f.write(line + "\n")
            os.replace(tmp, self.path)
        except OSError as e:
            self._log(f"[缓存] 重写缓存失败 {self.path}: {e}")
            try:
                tmp.unlink(missing_ok=True)
            except OSError:
                pass


def _line_entry(line):
    """缓存行 → (topic, payload)；坏行/缺字段返回 None。"""
    try:
        entry = json.loads(line)
    except ValueError:
        return None
    if not isinstance(entry, dict):
        return None
    topic, payload = entry.get("topic"), entry.get("payload")
    if not topic or not payload:
        return None
    return str(topic), str(payload)


def _track_window_of(payload):
    """track 报文 → (trackSessionId, windowIndex, sessionEnd)；非 track 返回 None。"""
    try:
        envelope = json.loads(payload)
    except (TypeError, ValueError):
        return None
    if not isinstance(envelope, dict) or envelope.get("type") != "track":
        return None
    data = envelope.get("data")
    if not isinstance(data, dict):
        return None
    return (str(data.get("trackSessionId") or ""),
            data.get("windowIndex"),
            data.get("sessionEnd") is True)


def _is_stale_track_line(line, topic, session_id, window_index) -> bool:
    """同 topic + 同 trackSessionId + sessionEnd 非 true + windowIndex 更小（缺省视为更旧）。"""
    if topic not in line:      # 便宜的预过滤：非本主题行不解析 JSON
        return False
    entry = _line_entry(line)
    if entry is None or entry[0] != topic:
        return False
    info = _track_window_of(entry[1])
    if info is None:
        return False
    line_session, line_window, line_end = info
    if line_end or line_session != session_id:
        return False
    if not isinstance(window_index, int) or isinstance(window_index, bool):
        return False      # 新报文索引异常：不清理，保数据
    if not isinstance(line_window, int) or isinstance(line_window, bool):
        return True       # 缺 windowIndex 的旧条目按"更旧"处理
    return line_window < window_index
