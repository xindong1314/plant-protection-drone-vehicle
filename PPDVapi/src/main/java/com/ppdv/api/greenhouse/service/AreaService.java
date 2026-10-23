package com.ppdv.api.greenhouse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppdv.api.archive.service.ArchiveService;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
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

    private static final String DEFAULT_COORDINATE_SYSTEM = "GCJ-02";
    private static final Set<String> SUPPORTED_COORDINATE_SYSTEMS = Set.of(DEFAULT_COORDINATE_SYSTEM);
    private static final int MIN_BOUNDARY_VERTICES = 3;
    private static final int MAX_BOUNDARY_VERTICES = 1_000;
    private static final int MAX_BOUNDARY_BYTES = 64 * 1024;
    private static final double MIN_POLYGON_AREA = 1e-12;

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

    @Transactional(readOnly = true)
    public List<GreenhouseArea> list() {
        return repository.findAllByOrderBySortOrderAsc();
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
        normalizeAndValidateAreaFields(area);
        if (repository.existsByAreaCode(area.getAreaCode())) {
            throw new IllegalStateException("区域编号已存在: " + area.getAreaCode());
        }
        // 风险字段只允许由风险引擎和闭环业务更新，创建接口不信任客户端值。
        area.setRiskStatus("NORMAL");
        area.setRiskScore(0);
        area.setLastEvalAt(null);
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
        if (area.getCoordinateSystem() != null && !area.getCoordinateSystem().isBlank()) {
            validateCoordinateSystem(area.getCoordinateSystem());
        }
        if (area.getBoundaryJson() != null) {
            validateBoundary(area.getBoundaryJson());
        }
        db.setAreaCode(area.getAreaCode());
        db.setAreaName(area.getAreaName());
        db.setGreenhouseName(area.getGreenhouseName());
        // 管理端的 GIS 编辑请求不会携带 arucoId，缺字段时必须保留已有绑定。
        if (area.getArucoId() != null) {
            db.setArucoId(area.getArucoId());
        }
        if (area.getBoundaryJson() != null) {
            db.setBoundaryJson(area.getBoundaryJson());
        }
        if (area.getCoordinateSystem() != null && !area.getCoordinateSystem().isBlank()) {
            db.setCoordinateSystem(area.getCoordinateSystem().trim());
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

    @Transactional
    public void updateRiskStatus(Long areaId, String riskStatus) {
        GreenhouseArea area = get(areaId);
        area.setRiskStatus(riskStatus);
        repository.save(area);
        archiveService.updateRiskStatus(areaId, riskStatus);
    }

    /** 更新风险评分 + 状态 + 评估时间。 */
    @Transactional
    public void updateRiskScoreAndStatus(Long areaId, int score, String status) {
        GreenhouseArea area = get(areaId);
        area.setRiskScore(score);
        area.setRiskStatus(status);
        area.setLastEvalAt(LocalDateTime.now());
        repository.save(area);
        archiveService.updateRiskStatus(areaId, status);
    }

    private void validateCoordinateSystem(String coordinateSystem) {
        String normalized = coordinateSystem == null ? null : coordinateSystem.trim();
        if (!SUPPORTED_COORDINATE_SYSTEMS.contains(normalized)) {
            throw new IllegalArgumentException("不支持的坐标系: " + coordinateSystem
                    + "，当前仅支持 " + String.join("、", SUPPORTED_COORDINATE_SYSTEMS));
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
