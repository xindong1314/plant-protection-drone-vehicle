package com.ppdv.api.greenhouse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppdv.api.device.Device;
import com.ppdv.api.device.DeviceService;
import com.ppdv.api.device.DeviceStatus;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 作业路线录制会话（纯内存，单实例）：开始后随 tele/state 遥测持续记录小车坐标，
 * 结束时把点集序列化为路线 JSON 交给 {@link AreaService#saveRecordedRoute} 按区域落库。
 * 中间点不落库、不加表；同一小车或同一区域同时只能有一条录制会话。
 * 多实例/负载均衡部署不受支持（v1.1 评审 M7），滚动重启会丢失未结束的会话。
 */
@Service
public class RouteRecordingService {

    private static final Logger log = LoggerFactory.getLogger(RouteRecordingService.class);

    private static final int MIN_ROUTE_POINTS = 2;
    /** v1.1 评审 S4：空闲超时（按服务器时钟 lastSeenAt 起算）与最大录制时长，防止死会话永久占用设备。 */
    private static final long IDLE_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final long MAX_SESSION_MS = 2 * 60 * 60 * 1000L;
    /** v1.1 评审 S3：坐标统一 7 位小数（与 V10 精度一致），再叠加字节预算兜底。 */
    private static final int COORDINATE_SCALE = 7;
    private static final int ROUTE_BYTES_MARGIN = 1024;

    private final AreaService areaService;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;
    private final LongSupplier clock;

    /** 会话容器：key 为 deviceId。 */
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    // v1.1 评审修复#1：类里有多个构造器时 Spring 不会自动挑选，必须显式标注注入点，
    // 否则回落无参构造器导致整个应用启动失败；四参构造器仅供同包单测注入假时钟。
    @Autowired
    public RouteRecordingService(AreaService areaService, DeviceService deviceService, ObjectMapper objectMapper) {
        this(areaService, deviceService, objectMapper, System::currentTimeMillis);
    }

    RouteRecordingService(AreaService areaService, DeviceService deviceService, ObjectMapper objectMapper,
                          LongSupplier clock) {
        this.areaService = areaService;
        this.deviceService = deviceService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 开始录制：校验区域坐标系与设备在线状态，并保证设备与区域都未被其他会话占用。 */
    public void start(Long areaId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("设备编号不能为空");
        }
        String normalizedDeviceId = deviceId.trim();
        purgeExpired();
        GreenhouseArea area = areaService.get(areaId);
        // 路线按 CGCS2000 遥测录制；V10 保留的 GCJ-02 坏边界区域必须先重新圈定边界。
        if (!AreaService.DEFAULT_COORDINATE_SYSTEM.equals(area.getCoordinateSystem())) {
            throw new IllegalArgumentException("区域坐标系不是 CGCS2000，请先重新圈定边界: " + area.getAreaCode());
        }
        Device device = deviceService.get(normalizedDeviceId);
        if (device.getStatus() != DeviceStatus.ONLINE) {
            throw new IllegalStateException("设备不在线，无法录制路线: " + normalizedDeviceId);
        }
        synchronized (sessions) {
            for (Session session : sessions.values()) {
                if (session.deviceId.equals(normalizedDeviceId)) {
                    throw new IllegalStateException("设备已在录制路线: " + normalizedDeviceId);
                }
                if (session.areaId.equals(areaId)) {
                    throw new IllegalStateException("区域已在录制路线: " + area.getAreaCode());
                }
            }
            sessions.put(normalizedDeviceId, new Session(areaId, area.getAreaCode(), normalizedDeviceId, clock.getAsLong()));
        }
    }

    /**
     * 追加一个遥测定位点（按 [lon,lat] 保存）：无会话、坐标无效、早于最近入库点的补传
     * （v1.1 评审 M5）、与上一点完全相同、达到点数上限或序列化字节预算（v1.1 评审 S3）
     * 时忽略；整个方法吞掉自身异常（warn 日志），绝不影响遥测主链路。
     * 服务器时钟的 lastSeenAt 在坐标有效后立即刷新（评审修复#2）：补传、重复、封顶分支
     * 都说明设备仍在上报，不能让会话因“未新增点”被空闲超时静默清理。
     */
    public void append(String deviceId, Double lat, Double lon, long ts) {
        try {
            if (deviceId == null) {
                return;
            }
            Session session = sessions.get(deviceId);
            if (session == null) {
                return;
            }
            if (lat == null || lon == null || !Double.isFinite(lat) || !Double.isFinite(lon)
                    || lon < -180 || lon > 180 || lat < -90 || lat > 90) {
                return;
            }
            synchronized (session) {
                long now = clock.getAsLong();
                if (isExpired(session, now)) {
                    // 两参原子删除：并发 start 可能刚用新会话顶替了本会话，不能按 key 误删（评审修复#10）。
                    sessions.remove(deviceId, session);
                    log.warn("路线录制会话超时自动结束: device={}, areaId={}", deviceId, session.areaId);
                    return;
                }
                session.lastSeenAt = now;
                // 补传过滤只比较设备时钟的最近入库点；lastFixTs 为 0（尚无有效点）时不过滤，
                // 首点无条件接受，设备时钟偏慢不再丢点（评审修复#3）。
                if (session.lastFixTs != 0 && ts < session.lastFixTs) {
                    return;
                }
                List<double[]> points = session.points;
                // 达到落库校验点数上限后忽略新点，避免录制无限累积导致 stop 必然失败。
                if (points.size() >= AreaService.MAX_ROUTE_POINTS) {
                    return;
                }
                double roundedLon = round(lon);
                double roundedLat = round(lat);
                if (!points.isEmpty()) {
                    double[] last = points.get(points.size() - 1);
                    if (last[0] == roundedLon && last[1] == roundedLat) {
                        return;
                    }
                }
                int pointBytes = objectMapper.writeValueAsBytes(new double[] {roundedLon, roundedLat}).length + 1;
                if (session.approxBytes + pointBytes > AreaService.MAX_ROUTE_BYTES - ROUTE_BYTES_MARGIN) {
                    // 字节预算兜底：20000 点 + 高精度坐标可能超过落库 512KB 校验，超预算直接忽略，
                    // 保证 stop 序列化后必然通过 validateRouteJson。
                    if (!session.byteBudgetWarned) {
                        session.byteBudgetWarned = true;
                        log.warn("路线录制达到字节上限，忽略后续定位点: device={}, areaId={}", deviceId, session.areaId);
                    }
                    return;
                }
                points.add(new double[] {roundedLon, roundedLat});
                session.approxBytes += pointBytes;
                session.lastFixTs = ts;
            }
        } catch (Exception e) {
            // v1.1 评审 L8：生产级别下不再静默，warn 便于发现录制少点问题。
            log.warn("路线录制坐标追加失败: device={}, err={}", deviceId, e.getMessage());
        }
    }

    /**
     * 结束录制：点数不足时保留会话供继续行驶补点（v1.1 评审 S2）；
     * 校验/落库失败为终态不留死会话。落库在会话全局锁内完成（评审修复#12，撤销 M6 的锁外落库），
     * 与 cancel/删区域并发时在锁上串行化，不会出现“取消成功但路线仍被保存”；
     * 落库只做单行更新，且 append 不取全局锁，遥测链路不受影响。成功返回点数。
     */
    public int stop(Long areaId) {
        purgeExpired();
        synchronized (sessions) {
            Session session = findByAreaId(areaId);
            if (session == null) {
                throw new IllegalArgumentException("区域没有进行中的路线录制: " + areaId);
            }
            List<double[]> points;
            synchronized (session) {
                points = new ArrayList<>(session.points);
            }
            if (points.size() < MIN_ROUTE_POINTS) {
                // 点数不足保留会话：用户可继续录制补点，或显式取消录入。
                throw new IllegalArgumentException(
                        "路线有效点数不足，至少需要 " + MIN_ROUTE_POINTS + " 个点，请继续录制或取消录入");
            }
            try {
                areaService.saveRecordedRoute(areaId, serialize(points));
            } finally {
                sessions.remove(session.deviceId, session);
            }
            return points.size();
        }
    }

    /** 取消录制：仅移除会话不落库；无会话时幂等成功（v1.1 评审 S2）。 */
    public void cancel(Long areaId) {
        purgeExpired();
        synchronized (sessions) {
            Session session = findByAreaId(areaId);
            if (session != null) {
                sessions.remove(session.deviceId, session);
            }
        }
    }

    /** 设备被删除时联动取消其录制会话（v1.1 评审 S4），幂等。 */
    public void cancelForDevices(Collection<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        synchronized (sessions) {
            for (String deviceId : deviceIds) {
                if (deviceId != null && sessions.remove(deviceId) != null) {
                    log.warn("设备已删除，取消其路线录制会话: device={}", deviceId);
                }
            }
        }
    }

    /** 设备是否存在进行中的录制会话（v1.1 评审 L4：录制中的设备不允许下发任务）。 */
    public boolean hasActiveSessionForDevice(String deviceId) {
        if (deviceId == null) {
            return false;
        }
        // 评审修复#4：先懒清理过期会话，死会话不再把任务下发长期误拒为 409。
        purgeExpired();
        return sessions.containsKey(deviceId);
    }

    /** 全部活动录制会话快照。 */
    public List<RouteRecordingStatus> status() {
        purgeExpired();
        synchronized (sessions) {
            List<RouteRecordingStatus> result = new ArrayList<>(sessions.size());
            for (Session session : sessions.values()) {
                synchronized (session) {
                    result.add(new RouteRecordingStatus(session.areaId, session.areaCode, session.deviceId,
                            session.startedAt, session.lastFixTs, session.points.size()));
                }
            }
            return result;
        }
    }

    private void purgeExpired() {
        long now = clock.getAsLong();
        synchronized (sessions) {
            sessions.values().removeIf(session -> {
                synchronized (session) {
                    return isExpired(session, now);
                }
            });
        }
    }

    private boolean isExpired(Session session, long now) {
        // 空闲超时只看服务器时钟的 lastSeenAt；lastFixTs 是设备时钟，仅用于补传过滤（评审修复#2/#3）。
        return now - session.lastSeenAt >= IDLE_TIMEOUT_MS || now - session.startedAt >= MAX_SESSION_MS;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    private Session findByAreaId(Long areaId) {
        for (Session session : sessions.values()) {
            if (session.areaId.equals(areaId)) {
                return session;
            }
        }
        return null;
    }

    private String serialize(List<double[]> points) {
        try {
            return objectMapper.writeValueAsString(points);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("路线序列化失败", e);
        }
    }

    /** 路线录制状态（startedAt/lastFixTs 为 epoch 毫秒；lastFixTs 为设备时钟，0 表示尚未收到有效点）。 */
    public record RouteRecordingStatus(Long areaId, String areaCode, String deviceId,
                                       long startedAt, long lastFixTs, int pointCount) {
    }

    private static final class Session {
        private final Long areaId;
        private final String areaCode;
        private final String deviceId;
        private final long startedAt;
        private final List<double[]> points = new ArrayList<>();
        /** 服务器时钟的最近有效遥测时间：空闲超时依据，任何坐标有效的上报都刷新。 */
        private volatile long lastSeenAt;
        /** 设备时钟的最近入库点 ts：仅用于补传过滤；0 表示尚未收到有效点。 */
        private volatile long lastFixTs;
        /** 已录点序列化字节数估算（含逗号），用于 512KB 落库预算兜底。 */
        private int approxBytes;
        private volatile boolean byteBudgetWarned;

        private Session(Long areaId, String areaCode, String deviceId, long startedAt) {
            this.areaId = areaId;
            this.areaCode = areaCode;
            this.deviceId = deviceId;
            this.startedAt = startedAt;
            this.lastSeenAt = startedAt;
        }
    }
}
