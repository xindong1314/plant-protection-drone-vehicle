package com.ppdv.api.task.service;

import com.ppdv.api.archive.service.ArchiveService;
import com.ppdv.api.greenhouse.service.AreaService;
import com.ppdv.api.task.domain.ReviewRecord;
import com.ppdv.api.task.domain.SprayTask;
import com.ppdv.api.task.repository.ReviewRecordRepository;
import com.ppdv.api.task.repository.SprayTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PlantProtectionService {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "PENDING", Set.of("ASSIGNED", "FAILED"),
            "ASSIGNED", Set.of("IN_PROGRESS", "FAILED"),
            "IN_PROGRESS", Set.of("COMPLETED", "FAILED"),
            "COMPLETED", Set.of(),
            "FAILED", Set.of()
    );

    private final SprayTaskRepository sprayRepository;
    private final ReviewRecordRepository reviewRepository;
    private final ArchiveService archiveService;
    private final AreaService areaService;

    public PlantProtectionService(SprayTaskRepository sprayRepository,
                                  ReviewRecordRepository reviewRepository,
                                  ArchiveService archiveService,
                                  AreaService areaService) {
        this.sprayRepository = sprayRepository;
        this.reviewRepository = reviewRepository;
        this.archiveService = archiveService;
        this.areaService = areaService;
    }

    // ---- 点喷任务 ----

    @Transactional(readOnly = true)
    public Page<SprayTask> listSprayTasks(Pageable pageable) {
        return sprayRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public SprayTask getSprayTask(Long id) {
        return sprayRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("点喷任务不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<SprayTask> getPendingSprayTasks() {
        return sprayRepository.findByStatusOrderByCreateTimeAsc("PENDING");
    }

    @Transactional
    public SprayTask createSprayTask(SprayTask task) {
        task.setStatus("PENDING");
        return sprayRepository.save(task);
    }

    @Transactional
    public SprayTask assignSprayTask(Long id, String deviceId) {
        SprayTask task = getSprayTask(id);
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("设备编号不能为空");
        }
        ensureTransition(task.getStatus(), "ASSIGNED");
        task.setDeviceId(deviceId);
        task.setStatus("ASSIGNED");
        task.setAssignedAt(LocalDateTime.now());
        return sprayRepository.save(task);
    }

    @Transactional
    public SprayTask updateSprayTaskStatus(Long id, String status) {
        SprayTask task = getSprayTask(id);
        if (status == null || !ALLOWED_TRANSITIONS.containsKey(status)) {
            throw new IllegalArgumentException("无效的任务状态: " + status);
        }
        if (status.equals(task.getStatus())) {
            return task;
        }
        ensureTransition(task.getStatus(), status);
        task.setStatus(status);
        if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
            task.setCompletedAt(LocalDateTime.now());
        }
        SprayTask saved = sprayRepository.save(task);
        // 联动：点喷完成 → 自动生成复查任务 + 更新区域档案
        if ("COMPLETED".equals(status) && task.getAreaId() != null) {
            ReviewRecord review = new ReviewRecord();
            review.setSprayTaskId(task.getId());
            review.setAreaId(task.getAreaId());
            review.setResult("NEED_RETRY");
            review.setReviewTime(LocalDateTime.now());
            reviewRepository.save(review);
            archiveService.touchSpray(task.getAreaId());
            areaService.updateRiskStatus(task.getAreaId(), "SPRAYED_PENDING_REVIEW");
        }
        // 联动：点喷失败 → 区域恢复高风险
        if ("FAILED".equals(status) && task.getAreaId() != null) {
            areaService.updateRiskStatus(task.getAreaId(), "HIGH_RISK");
        }
        return saved;
    }

    private void ensureTransition(String currentStatus, String targetStatus) {
        Set<String> allowed = ALLOWED_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new IllegalStateException("不允许的任务状态流转: " + currentStatus + " -> " + targetStatus);
        }
    }

    // ---- 复查记录 ----

    @Transactional
    public ReviewRecord createReview(ReviewRecord review) {
        review.setReviewTime(LocalDateTime.now());
        if (review.getResult() == null) {
            review.setResult("NEED_RETRY");
        }
        ReviewRecord saved = reviewRepository.save(review);
        // 联动：复查结果驱动状态流转
        if (saved.getAreaId() != null) {
            switch (saved.getResult()) {
                case "PASSED" -> {
                    areaService.updateRiskStatus(saved.getAreaId(), "NORMAL");
                }
                case "FAILED" -> {
                    areaService.updateRiskStatus(saved.getAreaId(), "HIGH_RISK");
                }
                case "NEED_RETRY" -> {
                    areaService.updateRiskStatus(saved.getAreaId(), "SPRAYED_PENDING_REVIEW");
                }
            }
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ReviewRecord> getReviewsBySprayTask(Long sprayTaskId) {
        return reviewRepository.findBySprayTaskIdOrderByReviewTimeDesc(sprayTaskId);
    }

    @Transactional(readOnly = true)
    public List<ReviewRecord> getReviewsByArea(Long areaId) {
        return reviewRepository.findByAreaIdOrderByReviewTimeDesc(areaId);
    }
}
