package com.ppdv.api.greenhouse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppdv.api.archive.service.ArchiveService;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.dto.AreaRoute;
import com.ppdv.api.greenhouse.dto.AreaSummary;
import com.ppdv.api.greenhouse.repository.AreaRepository;
import com.ppdv.api.inspection.repository.DetectionResultRepository;
import com.ppdv.api.inspection.repository.InspectionImageRepository;
import com.ppdv.api.inspection.repository.InspectionRecordRepository;
import com.ppdv.api.task.repository.ReviewRecordRepository;
import com.ppdv.api.task.repository.SprayTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class AreaService {

    // 包内可见：RouteRecordingService 复用同一坐标系要求与路线点数上限，避免两处口径漂移。
    static final String DEFAULT_COORDINATE_SYSTEM = "CGCS2000";
    private static final Set<String> SUPPORTED_COORDINATE_SYSTEMS = Set.of(DEFAULT_COORDINATE_SYSTEM);
    private static final int MIN_BOUNDARY_VERTICES = 3;
    private static final int MAX_BOUNDARY_VERTICES = 1_000;
    private static final int MAX_BOUNDARY_BYTES = 64 * 1024;
    private static final double MIN_POLYGON_AREA = 1e-12;
    private static final int MIN_ROUTE_POINTS = 2;
    static final int MAX_ROUTE_POINTS = 20_000;
    // 包内可见：RouteRecordingService 在 append 时按同一字节上限做预算兜底，避免口径漂移。
    static final int MAX_ROUTE_BYTES = 512 * 1024;

    private final AreaRepository repository;
    private final ArchiveService archiveService;
    private final InspectionRecordRepository inspectionRepository;
    private final InspectionImageRepository imageRepository;
    private final DetectionResultRepository detectionRepository;
    private final SprayTaskRepository sprayRepository;
    private final ReviewRecordRepository reviewRepository;
    private final ObjectMapper objectMapper;

    public AreaService(AreaRepository repository,
                       ArchiveService archiveService,
                       InspectionRecordRepository inspectionRepository,
                       InspectionImageRepository imageRepository,
                       DetectionResultRepository detectionRepository,
                       SprayTaskRepository sprayRepository,
                       ReviewRecordRepository reviewRepository,
                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.archiveService = archiveService;
        this.inspectionRepository = inspectionRepository;
        this.imageRepository = imageRepository;
        this.detectionRepository = detectionRepository;
        this.sprayRepository = sprayRepository;
        this.reviewRepository = reviewRepository;
        this.objectMapper = objectMapper;
    }

    /** 区域列表摘要（v1.1 评审 M1）：不携带 route_json 全文，路线按 id 另行拉取。 */
    @Transactional(readOnly = true)
    public List<AreaSummary> list() {
        return repository.findAllSummaries();
    }

    /** 单个区域的完整作业路线（v1.1 评审 M1）：规划页选中区域后按 id 拉取。 */
    @Transactional(readOnly = true)
    public AreaRoute getRoute(Long id) {
        GreenhouseArea area = get(id);
        return new AreaRoute(area.getId(), area.getRouteJson(), area.getRouteRecordedAt());
    }

    /** 全部已录路线（评审修复#11）：区域页地图一次批量拉取，替代逐区域的并行 N+1 请求。 */
    @Transactional(readOnly = true)
    public List<AreaRoute> listRoutes() {
        return repository.findAllWithRoute().stream()
                .map(area -> new AreaRoute(area.getId(), area.getRouteJson(), area.getRouteRecordedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GreenhouseArea get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("区域不存在: " + id));
    }

    @Transactional(readOnly = true)
    public GreenhouseArea getByCode(String areaCode) {
        return repository.findByAreaCode(areaCode)
                .orElseThrow(() -> new IllegalArgumentException("区域不存在: " + areaCode));
    }

    /** 按关键字模糊匹配区域编号/名称，返回命中的区域 ID；关键字为空白时返回空列表。 */
    @Transactional(readOnly = true)
    public List<Long> findAreaIdsByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return repository.findIdsByKeywordPattern("%" + keyword.trim() + "%");
    }

    /** 在当前事务内锁定区域，供需要基于区域状态做“检查后写入”的业务使用。 */
    @Transactional
    public GreenhouseArea getForUpdate(Long id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("区域不存在: " + id));
    }

    @Transactional
    public GreenhouseArea create(GreenhouseArea area) {
        if (area == null) {
            throw new IllegalArgumentException("区域信息不能为空");
        }
        if (area.getBoundaryJson() != null && area.getBoundaryJson().isBlank()) {
            // 创建接口不区分“未圈定”与“清除”，空串统一按未圈定处理。
            area.setBoundaryJson(null);
        }
        normalizeAndValidateAreaFields(area);
        if (repository.existsByAreaCode(area.getAreaCode())) {
            throw new IllegalStateException("区域编号已存在: " + area.getAreaCode());
        }
        // 风险字段只允许由风险引擎和闭环业务更新，创建接口不信任客户端值。
        area.setRiskStatus("NORMAL");
        area.setRiskScore(0);
        area.setLastEvalAt(null);
        // 作业路线只能由遥测录制闭环写入，创建接口同样不信任客户端值。
        area.setRouteJson(null);
        area.setRoutePointCount(null);
        area.setRouteRecordedAt(null);
        if (area.getSortOrder() == null) {
            area.setSortOrder(0);
        }
        if (area.getCoordinateSystem() == null || area.getCoordinateSystem().isBlank()) {
            area.setCoordinateSystem(DEFAULT_COORDINATE_SYSTEM);
        }
        validateCoordinateSystem(area.getCoordinateSystem());
        if (area.getBoundaryJson() != null) {
            validateBoundary(area.getBoundaryJson());
        }

        area.setId(null);
        area.setCoordinateSystem(area.getCoordinateSystem().trim());
        GreenhouseArea saved = repository.save(area);
        archiveService.createForArea(saved.getId(), saved.getRiskStatus(), saved.getCreateBy());
        return saved;
    }

    @Transactional
    public GreenhouseArea update(GreenhouseArea area) {
        if (area == null || area.getId() == null) {
            throw new IllegalArgumentException("区域 ID 不能为空");
        }
        normalizeAndValidateAreaFields(area);
        GreenhouseArea db = getForUpdate(area.getId());
        if (!area.getAreaCode().equals(db.getAreaCode())
                && repository.existsByAreaCodeAndIdNot(area.getAreaCode(), area.getId())) {
            throw new IllegalStateException("区域编号已存在: " + area.getAreaCode());
        }
        String requestCoordinateSystem = area.getCoordinateSystem();
        if (requestCoordinateSystem != null && !requestCoordinateSystem.isBlank()) {
            validateCoordinateSystem(requestCoordinateSystem);
        }
        // v1.1 评审 L7：boundaryJson 传空字符串表示显式清除边界；null 仍表示保持原边界。
        boolean clearBoundary = "".equals(area.getBoundaryJson());
        if (area.getBoundaryJson() != null && !clearBoundary) {
            validateBoundary(area.getBoundaryJson());
        }
        // v1.1 评审 S1 + 评审修复#8：边界与坐标系绑定——边界未真正重画（null 保持原值，或原样
        // 回传与库内完全相同的旧边界）时禁止改标坐标系，否则 V10 保留的 GCJ-02 坏边界会被静默改标。
        boolean boundaryReplaced = !clearBoundary && area.getBoundaryJson() != null
                && !area.getBoundaryJson().equals(db.getBoundaryJson());
        if (!clearBoundary && !boundaryReplaced
                && db.getBoundaryJson() != null
                && requestCoordinateSystem != null && !requestCoordinateSystem.isBlank()
                && !requestCoordinateSystem.trim().equals(db.getCoordinateSystem())) {
            throw new IllegalArgumentException("区域已有边界，变更坐标系必须重新圈定边界: " + db.getAreaCode());
        }
        db.setAreaCode(area.getAreaCode());
        db.setAreaName(area.getAreaName());
        db.setGreenhouseName(area.getGreenhouseName());
        // 管理端的 GIS 编辑请求不会携带 arucoId，缺字段时必须保留已有绑定。
        if (area.getArucoId() != null) {
            db.setArucoId(area.getArucoId());
        }
        if (clearBoundary) {
            db.setBoundaryJson(null);
            // 边界清除后不再有旧坐标系坏数据，坐标系可归位 CGCS2000。
            db.setCoordinateSystem(DEFAULT_COORDINATE_SYSTEM);
        } else if (area.getBoundaryJson() != null) {
            db.setBoundaryJson(area.getBoundaryJson());
            if (boundaryReplaced) {
                // 评审修复#8：新圈定的边界一律按 CGCS2000 存储；直调 API 未声明坐标系时补默认值，
                // 防止新边界沿用旧 GCJ-02 标签导致路线永远无法录制。未变化的边界不改坐标系。
                db.setCoordinateSystem(requestCoordinateSystem != null && !requestCoordinateSystem.isBlank()
                        ? requestCoordinateSystem.trim()
                        : DEFAULT_COORDINATE_SYSTEM);
            } else if (requestCoordinateSystem != null && !requestCoordinateSystem.isBlank()) {
                db.setCoordinateSystem(requestCoordinateSystem.trim());
            }
        }
        if (area.getSortOrder() != null) {
            db.setSortOrder(area.getSortOrder());
        }
        db.setRemark(area.getRemark());
        db.setUpdateBy(area.getUpdateBy());
        return repository.save(db);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("区域 ID 不能为空");
        }
        GreenhouseArea area = getForUpdate(id);
        List<String> references = new ArrayList<>();
        if (inspectionRepository.existsByAreaId(id)) {
            references.add("巡检记录");
        }
        if (imageRepository.existsByAreaId(id)) {
            references.add("巡检图片");
        }
        if (detectionRepository.existsByAreaId(id)) {
            references.add("检测结果");
        }
        if (sprayRepository.existsByAreaId(id)) {
            references.add("点喷任务");
        }
        if (reviewRepository.existsByAreaId(id)) {
            references.add("复查记录");
        }
        if (!references.isEmpty()) {
            throw new IllegalStateException("区域存在关联业务数据，不能删除: " + String.join("、", references));
        }

        archiveService.deleteForArea(id);
        repository.delete(area);
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return id != null && repository.existsById(id);
    }

    @Transactional
    public void updateRiskStatus(Long areaId, String riskStatus) {
        // v1.1 评审 S6：锁行更新，避免与路线落库并发时全列覆盖丢失 route_json。
        GreenhouseArea area = getForUpdate(areaId);
        area.setRiskStatus(riskStatus);
        repository.save(area);
        archiveService.updateRiskStatus(areaId, riskStatus);
    }

    /** 更新风险评分 + 状态 + 评估时间。 */
    @Transactional
    public void updateRiskScoreAndStatus(Long areaId, int score, String status) {
        // v1.1 评审 S6：锁行更新，避免与路线落库并发时全列覆盖丢失 route_json。
        GreenhouseArea area = getForUpdate(areaId);
        area.setRiskScore(score);
        area.setRiskStatus(status);
        area.setLastEvalAt(LocalDateTime.now());
        repository.save(area);
        archiveService.updateRiskStatus(areaId, status);
    }

    /** 保存遥测录制的作业路线（CGCS2000）：路线、点数与录入时间只能由本方法写入。 */
    @Transactional
    public void saveRecordedRoute(Long areaId, String routeJson) {
        GreenhouseArea area = getForUpdate(areaId);
        requireCgcs2000Boundary(area);
        int pointCount = validateRouteJson(routeJson);
        area.setRouteJson(routeJson);
        // 评审修复#11：点数冗余列随落库维护，列表摘要不再对 512KB 文本逐行 JSON_LENGTH。
        area.setRoutePointCount(pointCount);
        area.setRouteRecordedAt(LocalDateTime.now());
        repository.save(area);
    }

    private void validateCoordinateSystem(String coordinateSystem) {
        String normalized = coordinateSystem == null ? null : coordinateSystem.trim();
        if (!SUPPORTED_COORDINATE_SYSTEMS.contains(normalized)) {
            throw new IllegalArgumentException("不支持的坐标系: " + coordinateSystem
                    + "，当前仅支持 " + String.join("、", SUPPORTED_COORDINATE_SYSTEMS));
        }
    }

    /** 路线按 CGCS2000 遥测录制；V10 保留的 GCJ-02 坏边界区域必须先重新圈定边界。 */
    private void requireCgcs2000Boundary(GreenhouseArea area) {
        if (!DEFAULT_COORDINATE_SYSTEM.equals(area.getCoordinateSystem())) {
            throw new IllegalArgumentException("区域坐标系不是 CGCS2000，请先重新圈定边界: " + area.getAreaCode());
        }
    }

    private void validateBoundary(String boundaryJson) {
        if (boundaryJson.isBlank()) {
            throw new IllegalArgumentException("区域边界不能为空字符串");
        }
        if (boundaryJson.getBytes(StandardCharsets.UTF_8).length > MAX_BOUNDARY_BYTES) {
            throw new IllegalArgumentException("区域边界数据过大，最大允许 " + MAX_BOUNDARY_BYTES + " 字节");
        }

        final JsonNode boundary;
        try {
            boundary = objectMapper.readTree(boundaryJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("区域边界必须是合法 JSON", e);
        }
        if (boundary == null || !boundary.isArray()) {
            throw new IllegalArgumentException("区域边界必须是 [[经度,纬度], ...] 数组");
        }
        if (boundary.size() < MIN_BOUNDARY_VERTICES || boundary.size() > MAX_BOUNDARY_VERTICES) {
            throw new IllegalArgumentException("区域边界顶点数必须在 " + MIN_BOUNDARY_VERTICES
                    + " 到 " + MAX_BOUNDARY_VERTICES + " 之间");
        }

        List<BoundaryPoint> points = new ArrayList<>(boundary.size());
        Set<BoundaryPoint> uniquePoints = new java.util.HashSet<>();
        for (int i = 0; i < boundary.size(); i++) {
            JsonNode point = boundary.get(i);
            if (!point.isArray() || point.size() != 2
                    || !point.get(0).isNumber() || !point.get(1).isNumber()) {
                throw new IllegalArgumentException("区域边界第 " + (i + 1) + " 个顶点格式无效");
            }
            double longitude = point.get(0).doubleValue();
            double latitude = point.get(1).doubleValue();
            if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException("区域边界第 " + (i + 1) + " 个顶点经度超出 [-180,180]");
            }
            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException("区域边界第 " + (i + 1) + " 个顶点纬度超出 [-90,90]");
            }
            BoundaryPoint boundaryPoint = new BoundaryPoint(normalizeZero(longitude), normalizeZero(latitude));
            if (!uniquePoints.add(boundaryPoint)) {
                throw new IllegalArgumentException("区域边界不能包含重复顶点");
            }
            points.add(boundaryPoint);
        }

        double twiceArea = 0;
        for (int i = 0; i < points.size(); i++) {
            BoundaryPoint current = points.get(i);
            BoundaryPoint next = points.get((i + 1) % points.size());
            twiceArea += current.longitude() * next.latitude() - next.longitude() * current.latitude();
        }
        if (!Double.isFinite(twiceArea) || Math.abs(twiceArea) <= MIN_POLYGON_AREA * 2) {
            throw new IllegalArgumentException("区域边界不能退化为零面积多边形");
        }
    }

    private int validateRouteJson(String routeJson) {
        if (routeJson == null || routeJson.isBlank()) {
            throw new IllegalArgumentException("区域作业路线不能为空");
        }
        if (routeJson.getBytes(StandardCharsets.UTF_8).length > MAX_ROUTE_BYTES) {
            throw new IllegalArgumentException("区域作业路线数据过大，最大允许 " + MAX_ROUTE_BYTES + " 字节");
        }

        final JsonNode route;
        try {
            route = objectMapper.readTree(routeJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("区域作业路线必须是合法 JSON", e);
        }
        if (route == null || !route.isArray()) {
            throw new IllegalArgumentException("区域作业路线必须是 [[经度,纬度], ...] 数组");
        }
        if (route.size() < MIN_ROUTE_POINTS || route.size() > MAX_ROUTE_POINTS) {
            throw new IllegalArgumentException("区域作业路线点数必须在 " + MIN_ROUTE_POINTS
                    + " 到 " + MAX_ROUTE_POINTS + " 之间");
        }

        // 与 validateBoundary 的差异：允许首尾闭合与重复点，不做面积校验。
        for (int i = 0; i < route.size(); i++) {
            JsonNode point = route.get(i);
            if (!point.isArray() || point.size() != 2
                    || !point.get(0).isNumber() || !point.get(1).isNumber()) {
                throw new IllegalArgumentException("区域作业路线第 " + (i + 1) + " 个点格式无效");
            }
            double longitude = point.get(0).doubleValue();
            double latitude = point.get(1).doubleValue();
            if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException("区域作业路线第 " + (i + 1) + " 个点经度超出 [-180,180]");
            }
            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException("区域作业路线第 " + (i + 1) + " 个点纬度超出 [-90,90]");
            }
        }
        return route.size();
    }

    private void normalizeAndValidateAreaFields(GreenhouseArea area) {
        area.setAreaCode(requireTrimmed(area.getAreaCode(), "区域编号", 16));
        area.setAreaName(requireTrimmed(area.getAreaName(), "区域名称", 64));
        area.setGreenhouseName(optionalTrimmed(area.getGreenhouseName(), "所属大棚", 64));
        if (area.getArucoId() != null) {
            String arucoId = area.getArucoId().trim();
            if (arucoId.length() > 32) {
                throw new IllegalArgumentException("ArUco 标记长度不能超过 32 个字符");
            }
            area.setArucoId(arucoId);
        }
        if (area.getRemark() != null) {
            String remark = area.getRemark().trim();
            if (remark.length() > 500) {
                throw new IllegalArgumentException("备注长度不能超过 500 个字符");
            }
            area.setRemark(remark);
        }
    }

    private String requireTrimmed(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String optionalTrimmed(String value, String label, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private double normalizeZero(double value) {
        return value == 0d ? 0d : value;
    }

    private record BoundaryPoint(double longitude, double latitude) {
    }
}
