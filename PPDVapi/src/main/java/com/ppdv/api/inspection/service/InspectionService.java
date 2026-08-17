package com.ppdv.api.inspection.service;

import com.ppdv.api.archive.service.ArchiveService;
import com.ppdv.api.inspection.domain.DetectionResult;
import com.ppdv.api.risk.service.RiskEvaluationService;
import com.ppdv.api.inspection.domain.InspectionImage;
import com.ppdv.api.inspection.domain.InspectionRecord;
import com.ppdv.api.inspection.repository.DetectionResultRepository;
import com.ppdv.api.inspection.repository.InspectionImageRepository;
import com.ppdv.api.inspection.repository.InspectionRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InspectionService {

    private final InspectionRecordRepository recordRepository;
    private final InspectionImageRepository imageRepository;
    private final DetectionResultRepository detectionRepository;
    private final ArchiveService archiveService;
    private final RiskEvaluationService riskEvalService;

    public InspectionService(InspectionRecordRepository recordRepository,
                             InspectionImageRepository imageRepository,
                             DetectionResultRepository detectionRepository,
                             ArchiveService archiveService,
                             RiskEvaluationService riskEvalService) {
        this.recordRepository = recordRepository;
        this.imageRepository = imageRepository;
        this.detectionRepository = detectionRepository;
        this.archiveService = archiveService;
        this.riskEvalService = riskEvalService;
    }

    // ---- 巡检记录 ----

    @Transactional(readOnly = true)
    public Page<InspectionRecord> listRecords(Pageable pageable) {
        return recordRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public InspectionRecord getRecord(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("巡检记录不存在: " + id));
    }

    @Transactional
    public InspectionRecord startInspection(String deviceId, Long areaId) {
        InspectionRecord record = new InspectionRecord();
        record.setDeviceId(deviceId);
        record.setAreaId(areaId);
        record.setStartTime(LocalDateTime.now());
        record.setStatus("IN_PROGRESS");
        record.setImageCount(0);
        record.setAnomalyCount(0);
        return recordRepository.save(record);
    }

    @Transactional
    public InspectionRecord completeInspection(Long id) {
        InspectionRecord record = getRecord(id);
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
        InspectionImage image = new InspectionImage();
        image.setInspectionId(inspectionId);
        image.setAreaId(areaId);
        image.setFilePath(filePath);
        image.setCapturedAt(LocalDateTime.now());

        InspectionRecord record = getRecord(inspectionId);
        record.setImageCount(record.getImageCount() + 1);
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
        return detectionRepository.save(result);
    }

    @Transactional
    public List<DetectionResult> addDetections(List<DetectionResult> results) {
        if (results != null && !results.isEmpty()) {
            DetectionResult first = results.get(0);
            Long imageId = first.getImageId();
            InspectionRecord record = null;
            if (imageId != null) {
                record = imageRepository.findById(imageId)
                        .map(InspectionImage::getInspectionId)
                        .flatMap(recordRepository::findById).orElse(null);
            }
            int anomalyCount = 0;
            for (DetectionResult r : results) {
                if (isAnomalyCategory(r.getCategory())) {
                    anomalyCount++;
                }
            }
            if (record != null && anomalyCount > 0) {
                record.setAnomalyCount(record.getAnomalyCount() + anomalyCount);
                recordRepository.save(record);
                // 联动：触发风险评估引擎 + 更新档案异常统计
                if (record.getAreaId() != null) {
                    riskEvalService.evaluate(record.getAreaId());
                    archiveService.touchAnomaly(record.getAreaId(), anomalyCount);
                }
            }
        }
        return detectionRepository.saveAll(results);
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
}
