package com.ppdv.api.inspection.service;

import com.ppdv.api.archive.service.ArchiveService;
import com.ppdv.api.device.Device;
import com.ppdv.api.device.DeviceService;
import com.ppdv.api.device.DeviceStatus;
import com.ppdv.api.greenhouse.service.AreaService;
import com.ppdv.api.inspection.domain.DetectionResult;
import com.ppdv.api.risk.service.RiskEvaluationService;
import com.ppdv.api.inspection.domain.InspectionImage;
import com.ppdv.api.inspection.domain.InspectionRecord;
import com.ppdv.api.inspection.repository.DetectionResultRepository;
import com.ppdv.api.inspection.repository.InspectionImageRepository;
import com.ppdv.api.inspection.repository.InspectionRecordRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InspectionService {

    private static final Set<String> DETECTION_STATUSES = Set.of("PENDING", "CONFIRMED", "IGNORED");
    private static final Set<String> RECORD_STATUSES = Set.of("IN_PROGRESS", "COMPLETED");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createTime")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final InspectionRecordRepository recordRepository;
    private final InspectionImageRepository imageRepository;
    private final DetectionResultRepository detectionRepository;
    private final ArchiveService archiveService;
    private final RiskEvaluationService riskEvalService;
    private final DeviceService deviceService;
    private final AreaService areaService;

    public InspectionService(InspectionRecordRepository recordRepository,
                             InspectionImageRepository imageRepository,
                             DetectionResultRepository detectionRepository,
                             ArchiveService archiveService,
                             RiskEvaluationService riskEvalService,
                             DeviceService deviceService,
                             AreaService areaService) {
        this.recordRepository = recordRepository;
        this.imageRepository = imageRepository;
        this.detectionRepository = detectionRepository;
        this.archiveService = archiveService;
        this.riskEvalService = riskEvalService;
        this.deviceService = deviceService;
        this.areaService = areaService;
    }

    // ---- 巡检记录 ----

    @Transactional(readOnly = true)
    public Page<InspectionRecord> listRecords(String keyword, String status, Pageable pageable) {
        Pageable sortedPageable = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST)
                : pageable;
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (normalizedStatus != null && !RECORD_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("无效的巡检状态: " + status);
        }
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (normalizedKeyword == null && normalizedStatus == null) {
            return recordRepository.findAll(sortedPageable);
        }
        List<Long> areaIds = normalizedKeyword == null ? List.of() : areaService.findAreaIdsByKeyword(normalizedKeyword);
        return recordRepository.findAll(buildRecordSpec(normalizedKeyword, normalizedStatus, areaIds), sortedPageable);
    }

    /** 组装查询条件：关键字匹配设备编号或区域编号/名称（解析为区域 ID），状态精确匹配。 */
    private Specification<InspectionRecord> buildRecordSpec(String keyword, String status, List<Long> areaIds) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null) {
                Predicate deviceLike = cb.like(root.get("deviceId"), "%" + keyword + "%");
                ps.add(areaIds.isEmpty() ? deviceLike : cb.or(deviceLike, root.get("areaId").in(areaIds)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public InspectionRecord getRecord(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("巡检记录不存在: " + id));
    }

    @Transactional
    public InspectionRecord startInspection(String deviceId, Long areaId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("巡检设备编号不能为空");
        }
        String normalizedDeviceId = deviceId.trim();
        Device device = deviceService.get(normalizedDeviceId);
        if (device.getStatus() != DeviceStatus.ONLINE) {
            throw new IllegalStateException("设备不在线，无法开始巡检: " + normalizedDeviceId);
        }
        if (areaId != null) {
            areaService.getForUpdate(areaId);
        }

        InspectionRecord record = new InspectionRecord();
        record.setDeviceId(normalizedDeviceId);
        record.setAreaId(areaId);
        record.setStartTime(LocalDateTime.now());
        record.setStatus("IN_PROGRESS");
        record.setImageCount(0);
        record.setAnomalyCount(0);
        return recordRepository.save(record);
    }

    @Transactional
    public InspectionRecord completeInspection(Long id) {
        InspectionRecord record = getRecordForUpdate(id);
        if ("COMPLETED".equals(record.getStatus())) {
            return record;
        }
        if (!"IN_PROGRESS".equals(record.getStatus())) {
            throw new IllegalStateException("当前巡检状态不允许完成: " + record.getStatus());
        }
        record.setEndTime(LocalDateTime.now());
        record.setStatus("COMPLETED");
        InspectionRecord saved = recordRepository.save(record);
        if (record.getAreaId() != null) {
            archiveService.touchInspection(record.getAreaId());
        }
        return saved;
    }

    // ---- 巡检图片 ----

    @Transactional
    public InspectionImage addImage(Long inspectionId, Long areaId, String filePath) {
        InspectionRecord record = getRecordForUpdate(inspectionId);
        if (!"IN_PROGRESS".equals(record.getStatus())) {
            throw new IllegalStateException("仅进行中的巡检可上传图片");
        }
        if (areaId != null && !Objects.equals(areaId, record.getAreaId())) {
            throw new IllegalArgumentException("图片区域与巡检记录区域不一致");
        }

        InspectionImage image = new InspectionImage();
        image.setInspectionId(inspectionId);
        image.setAreaId(record.getAreaId());
        image.setFilePath(filePath);
        image.setCapturedAt(LocalDateTime.now());

        record.setImageCount((record.getImageCount() == null ? 0 : record.getImageCount()) + 1);
        recordRepository.save(record);

        return imageRepository.save(image);
    }

    @Transactional(readOnly = true)
    public List<InspectionImage> getImages(Long inspectionId) {
        return imageRepository.findByInspectionIdOrderByCapturedAtAsc(inspectionId);
    }

    // ---- 识别结果 ----

    @Transactional
    public DetectionResult addDetection(DetectionResult result) {
        if (result == null) {
            throw new IllegalArgumentException("识别结果不能为空");
        }
        return addDetections(List.of(result)).get(0);
    }

    @Transactional
    public List<DetectionResult> addDetections(List<DetectionResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        Set<Long> imageIds = new LinkedHashSet<>();
        for (DetectionResult result : results) {
            validateDetectionFields(result);
            imageIds.add(result.getImageId());
        }
        Map<Long, InspectionImage> imagesById = imageRepository.findAllById(imageIds).stream()
                .collect(Collectors.toMap(InspectionImage::getId, Function.identity()));

        Map<Long, Integer> anomalyCountByInspection = new LinkedHashMap<>();
        Map<Long, Long> areaByInspection = new LinkedHashMap<>();
        for (DetectionResult result : results) {
            InspectionImage image = imagesById.get(result.getImageId());
            if (image == null) {
                throw new IllegalArgumentException("巡检图片不存在: " + result.getImageId());
            }
            if (result.getAreaId() != null && !Objects.equals(result.getAreaId(), image.getAreaId())) {
                throw new IllegalArgumentException("识别结果区域与巡检图片区域不一致");
            }
            result.setId(null);
            result.setAreaId(image.getAreaId());
            if (image.getInspectionId() != null
                    && !"IGNORED".equals(result.getStatus())
                    && isAnomalyCategory(result.getCategory())) {
                anomalyCountByInspection.merge(image.getInspectionId(), 1, Integer::sum);
                areaByInspection.putIfAbsent(image.getInspectionId(), image.getAreaId());
            }
        }

        // 先落库并 flush，确保随后执行的风险评估能查询到本批识别结果。
        List<DetectionResult> saved = detectionRepository.saveAllAndFlush(results);

        for (Map.Entry<Long, Integer> entry : anomalyCountByInspection.entrySet()) {
            Long inspectionId = entry.getKey();
            int anomalyCount = entry.getValue();
            InspectionRecord record = getRecordForUpdate(inspectionId);
            record.setAnomalyCount((record.getAnomalyCount() == null ? 0 : record.getAnomalyCount()) + anomalyCount);
            recordRepository.save(record);
            Long areaId = record.getAreaId() != null ? record.getAreaId() : areaByInspection.get(inspectionId);
            if (areaId != null) {
                riskEvalService.evaluate(areaId);
                archiveService.touchAnomaly(areaId, anomalyCount);
            }
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DetectionResult> getDetectionsByImage(Long imageId) {
        return detectionRepository.findByImageIdOrderByIdAsc(imageId);
    }

    @Transactional(readOnly = true)
    public List<DetectionResult> getDetectionsByArea(Long areaId) {
        return detectionRepository.findByAreaIdOrderByIdDesc(areaId);
    }

    private boolean isAnomalyCategory(String category) {
        return category != null && (category.contains("病斑") || category.contains("虫害")
                || category.contains("黄化") || category.contains("萎蔫")
                || category.contains("disease") || category.contains("pest"));
    }

    private InspectionRecord getRecordForUpdate(Long id) {
        return recordRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("巡检记录不存在: " + id));
    }

    private void validateDetectionFields(DetectionResult result) {
        if (result == null) {
            throw new IllegalArgumentException("识别结果不能为空");
        }
        if (result.getImageId() == null) {
            throw new IllegalArgumentException("识别结果必须关联巡检图片");
        }
        if (result.getCategory() == null || result.getCategory().isBlank()) {
            throw new IllegalArgumentException("识别类别不能为空");
        }
        String category = result.getCategory().trim();
        if (category.length() > 32) {
            throw new IllegalArgumentException("识别类别长度不能超过 32 个字符");
        }
        result.setCategory(category);

        Double confidence = result.getConfidence();
        if (confidence == null || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("识别置信度必须在 [0,1] 之间");
        }
        String status = result.getStatus();
        if (status == null || status.isBlank()) {
            status = "PENDING";
        } else {
            status = status.trim().toUpperCase();
        }
        if (!DETECTION_STATUSES.contains(status)) {
            throw new IllegalArgumentException("无效的识别结果状态: " + result.getStatus());
        }
        result.setStatus(status);
    }
}
