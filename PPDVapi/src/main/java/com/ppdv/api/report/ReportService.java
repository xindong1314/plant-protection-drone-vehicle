package com.ppdv.api.report;

import com.ppdv.api.greenhouse.repository.AreaRepository;
import com.ppdv.api.inspection.repository.DetectionResultRepository;
import com.ppdv.api.inspection.repository.InspectionRecordRepository;
import com.ppdv.api.task.repository.ReviewRecordRepository;
import com.ppdv.api.task.repository.SprayTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final InspectionRecordRepository inspectionRepo;
    private final DetectionResultRepository detectionRepo;
    private final SprayTaskRepository sprayRepo;
    private final ReviewRecordRepository reviewRepo;
    private final AreaRepository areaRepo;

    public ReportService(InspectionRecordRepository inspectionRepo,
                         DetectionResultRepository detectionRepo,
                         SprayTaskRepository sprayRepo,
                         ReviewRecordRepository reviewRepo,
                         AreaRepository areaRepo) {
        this.inspectionRepo = inspectionRepo;
        this.detectionRepo = detectionRepo;
        this.sprayRepo = sprayRepo;
        this.reviewRepo = reviewRepo;
        this.areaRepo = areaRepo;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildReport(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> report = new LinkedHashMap<>();

        // 时间范围
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("start", startTime != null ? startTime.toString() : "不限");
        period.put("end", endTime != null ? endTime.toString() : "不限");
        report.put("period", period);

        // 巡检汇总
        var inspections = inspectionRepo.findAll();
        long insTotal = inspections.stream()
                .filter(r -> inRange(r.getStartTime(), startTime, endTime)).count();
        long insCompleted = inspections.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()) && inRange(r.getStartTime(), startTime, endTime)).count();
        Map<String, Object> insMap = new LinkedHashMap<>();
        insMap.put("total", insTotal);
        insMap.put("completed", insCompleted);
        insMap.put("inProgress", insTotal - insCompleted);
        report.put("inspections", insMap);

        // 异常汇总
        var detections = detectionRepo.findAll();
        var filteredDetections = detections.stream()
                .filter(d -> inRange(d.getCreateTime(), startTime, endTime)).toList();
        long anomalyTotal = filteredDetections.size();
        Map<String, Long> byCategory = filteredDetections.stream()
                .filter(d -> d.getCategory() != null)
                .collect(Collectors.groupingBy(
                        d -> d.getCategory(),
                        LinkedHashMap::new,
                        Collectors.counting()));
        Map<String, Object> anomMap = new LinkedHashMap<>();
        anomMap.put("total", anomalyTotal);
        anomMap.put("byCategory", byCategory);
        report.put("anomalies", anomMap);

        // 点喷任务汇总
        var sprays = sprayRepo.findAll();
        var filteredSprays = sprays.stream()
                .filter(s -> inRange(s.getCreateTime(), startTime, endTime)).toList();
        Map<String, Object> sprayMap = new LinkedHashMap<>();
        sprayMap.put("total", filteredSprays.size());
        sprayMap.put("completed", filteredSprays.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count());
        sprayMap.put("failed", filteredSprays.stream().filter(s -> "FAILED".equals(s.getStatus())).count());
        sprayMap.put("pending", filteredSprays.stream().filter(s -> "PENDING".equals(s.getStatus())).count());
        report.put("sprayTasks", sprayMap);

        // 复查汇总
        var reviews = reviewRepo.findAll();
        var filteredReviews = reviews.stream()
                .filter(r -> inRange(r.getReviewTime(), startTime, endTime)).toList();
        Map<String, Object> reviewMap = new LinkedHashMap<>();
        reviewMap.put("total", filteredReviews.size());
        reviewMap.put("passed", filteredReviews.stream().filter(r -> "PASSED".equals(r.getResult())).count());
        reviewMap.put("failed", filteredReviews.stream().filter(r -> "FAILED".equals(r.getResult())).count());
        reviewMap.put("needRetry", filteredReviews.stream().filter(r -> "NEED_RETRY".equals(r.getResult())).count());
        report.put("reviews", reviewMap);

        // 区域概览
        List<Map<String, Object>> areaList = areaRepo.findAllByOrderBySortOrderAsc().stream().map(area -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("areaCode", area.getAreaCode());
            m.put("areaName", area.getAreaName());
            m.put("riskStatus", area.getRiskStatus());
            m.put("totalInspections", inspections.stream()
                    .filter(r -> area.getId().equals(r.getAreaId())).count());
            m.put("totalAnomalies", detections.stream()
                    .filter(d -> area.getId().equals(d.getAreaId())).count());
            return m;
        }).toList();
        report.put("areas", areaList);

        return report;
    }

    private boolean inRange(LocalDateTime time, LocalDateTime start, LocalDateTime end) {
        if (time == null) return false;
        if (start != null && time.isBefore(start)) return false;
        if (end != null && time.isAfter(end)) return false;
        return true;
    }
}
