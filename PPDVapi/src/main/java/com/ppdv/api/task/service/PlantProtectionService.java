package com.ppdv.api.task.service;

import com.ppdv.api.archive.service.ArchiveService;
import com.ppdv.api.command.CommandService;
import com.ppdv.api.device.Device;
import com.ppdv.api.device.DeviceService;
import com.ppdv.api.device.DeviceStatus;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.service.AreaService;
import com.ppdv.api.inspection.domain.InspectionImage;
import com.ppdv.api.inspection.repository.InspectionImageRepository;
import com.ppdv.api.task.domain.ReviewRecord;
import com.ppdv.api.task.domain.SprayTask;
import com.ppdv.api.task.dto.ReviewTaskView;
import com.ppdv.api.task.repository.ReviewRecordRepository;
import com.ppdv.api.task.repository.SprayTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
    private static final Set<String> ACTIVE_SPRAY_STATUSES = Set.of("PENDING", "ASSIGNED", "IN_PROGRESS");
    private static final Set<String> REVIEW_RESULTS = Set.of("PASSED", "FAILED", "NEED_RETRY");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createTime")
            .and(Sort.by(Sort.Direction.DESC, "id"));
    private static final Sort LATEST_COMPLETED_FIRST = Sort.by(Sort.Direction.DESC, "completedAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final SprayTaskRepository sprayRepository;
    private final ReviewRecordRepository reviewRepository;
    private final ArchiveService archiveService;
    private final AreaService areaService;
    private final InspectionImageRepository imageRepository;
    private final DeviceService deviceService;
    private final CommandService commandService;

    public PlantProtectionService(SprayTaskRepository sprayRepository,
                                  ReviewRecordRepository reviewRepository,
                                  ArchiveService archiveService,
                                  AreaService areaService,
                                  InspectionImageRepository imageRepository,
                                  DeviceService deviceService,
                                  CommandService commandService) {
        this.sprayRepository = sprayRepository;
        this.reviewRepository = reviewRepository;
        this.archiveService = archiveService;
        this.areaService = areaService;
        this.imageRepository = imageRepository;
        this.deviceService = deviceService;
        this.commandService = commandService;
    }

    // ---- 点喷任务 ----

    @Transactional(readOnly = true)
    public Page<SprayTask> listSprayTasks(Pageable pageable) {
        return listSprayTasks(null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SprayTask> listSprayTasks(String status, Pageable pageable) {
        Pageable sortedPageable = withDefaultSort(pageable);
        if (status == null || status.isBlank()) {
            return sprayRepository.findAll(sortedPageable);
        }
        String normalizedStatus = status.trim().toUpperCase();
        if (!ALLOWED_TRANSITIONS.containsKey(normalizedStatus)) {
            throw new IllegalArgumentException("无效的任务状态: " + status);
        }
        return sprayRepository.findByStatus(normalizedStatus, sortedPageable);
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
        if (task.getAreaId() == null) {
            throw new IllegalArgumentException("点喷任务目标区域不能为空");
        }
        // 锁定区域，使同一区域的并发创建请求串行检查活动任务。
        GreenhouseArea area = areaService.getForUpdate(task.getAreaId());
        if (!"HIGH_RISK".equals(area.getRiskStatus())) {
            throw new IllegalStateException("仅高风险区域可创建点喷任务: " + area.getAreaCode());
        }
        if (sprayRepository.existsByAreaIdAndStatusIn(task.getAreaId(), ACTIVE_SPRAY_STATUSES)) {
            throw new IllegalStateException("该区域已有待处理或执行中的点喷任务");
        }
        // 新建接口不接受客户端伪造主键、执行设备或状态时间线。
        task.setId(null);
        task.setDeviceId(null);
        task.setStatus("PENDING");
        task.setAssignedAt(null);
        task.setCompletedAt(null);
        return sprayRepository.save(task);
    }

    @Transactional
    public SprayTask assignSprayTask(Long id, String deviceId) {
        SprayTask task = getSprayTaskForUpdate(id);
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("设备编号不能为空");
        }
        String normalizedDeviceId = deviceId.trim();
        Device device = deviceService.get(normalizedDeviceId);
        if (device.getStatus() != DeviceStatus.ONLINE) {
            throw new IllegalStateException("设备不在线，无法分配点喷任务: " + normalizedDeviceId);
        }
        ensureTransition(task.getStatus(), "ASSIGNED");
        task.setDeviceId(normalizedDeviceId);
        task.setStatus("ASSIGNED");
        task.setAssignedAt(LocalDateTime.now());
        return sprayRepository.save(task);
    }

    @Transactional
    public SprayTask updateSprayTaskStatus(Long id, String status) {
        SprayTask task = getSprayTaskForUpdate(id);
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

    /** 校验任务、区域和设备后下发点喷指令，areaCode 始终从任务关联区域读取。 */
    @Transactional
    public String executeSprayTask(Long id, String action, Integer duration) {
        if (!"start".equals(action)) {
            throw new IllegalArgumentException("执行点喷仅支持 start 动作");
        }
        if (duration == null || duration < 1 || duration > 3600) {
            throw new IllegalArgumentException("喷洒时长必须在 1 到 3600 秒之间");
        }

        SprayTask task = getSprayTaskForUpdate(id);
        ensureTransition(task.getStatus(), "IN_PROGRESS");
        if (task.getAreaId() == null) {
            throw new IllegalStateException("点喷任务未关联目标区域");
        }
        if (task.getDeviceId() == null || task.getDeviceId().isBlank()) {
            throw new IllegalStateException("点喷任务未分配执行设备");
        }

        GreenhouseArea area = areaService.get(task.getAreaId());
        Device device = deviceService.get(task.getDeviceId());
        if (device.getStatus() != DeviceStatus.ONLINE) {
            throw new IllegalStateException("设备不在线，无法执行点喷任务: " + task.getDeviceId());
        }

        String msgId = commandService.sendSpray(task.getDeviceId(), action, area.getAreaCode(), duration);
        task.setStatus("IN_PROGRESS");
        sprayRepository.save(task);
        return msgId;
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
        if (review.getSprayTaskId() == null) {
            throw new IllegalArgumentException("复查记录必须关联点喷任务");
        }
        SprayTask sprayTask = getSprayTask(review.getSprayTaskId());
        if (!"COMPLETED".equals(sprayTask.getStatus())) {
            throw new IllegalStateException("仅已完成的点喷任务可录入复查结论");
        }
        if (sprayTask.getAreaId() == null) {
            throw new IllegalStateException("点喷任务未关联目标区域");
        }
        if (review.getAreaId() != null && !Objects.equals(review.getAreaId(), sprayTask.getAreaId())) {
            throw new IllegalArgumentException("复查区域与点喷任务目标区域不一致");
        }
        if (review.getBeforeImageId() != null && Objects.equals(review.getBeforeImageId(), review.getAfterImageId())) {
            throw new IllegalArgumentException("喷前图片和喷后图片不能相同");
        }
        validateReviewImage(review.getBeforeImageId(), sprayTask, "喷前图片");
        validateReviewImage(review.getAfterImageId(), sprayTask, "喷后图片");

        review.setId(null);
        review.setAreaId(sprayTask.getAreaId());
        review.setReviewTime(LocalDateTime.now());
        if (review.getResult() == null) {
            review.setResult("NEED_RETRY");
        }
        if (!REVIEW_RESULTS.contains(review.getResult())) {
            throw new IllegalArgumentException("无效的复查结果: " + review.getResult());
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

    private SprayTask getSprayTaskForUpdate(Long id) {
        return sprayRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("点喷任务不存在: " + id));
    }

    /** 分页返回已完成点喷任务及其最新复查结论，固定两次查询，避免逐任务 N+1。 */
    @Transactional(readOnly = true)
    public Page<ReviewTaskView> listReviewTasks(Pageable pageable) {
        Pageable sortedPageable = withDefaultSort(pageable, LATEST_COMPLETED_FIRST);
        Page<SprayTask> taskPage = sprayRepository.findByStatus("COMPLETED", sortedPageable);
        List<Long> taskIds = taskPage.getContent().stream().map(SprayTask::getId).toList();

        Map<Long, ReviewRecord> latestByTask = new LinkedHashMap<>();
        if (!taskIds.isEmpty()) {
            reviewRepository.findBySprayTaskIdInOrderByReviewTimeDescIdDesc(taskIds)
                    .forEach(review -> latestByTask.putIfAbsent(review.getSprayTaskId(), review));
        }

        List<ReviewTaskView> rows = taskPage.getContent().stream().map(task -> {
            ReviewRecord review = latestByTask.get(task.getId());
            return new ReviewTaskView(
                    task.getId(),
                    task.getAreaId(),
                    task.getCompletedAt(),
                    review != null ? review.getReviewTime() : null,
                    review != null && review.getResult() != null ? review.getResult() : "NEED_RETRY",
                    review != null ? review.getBeforeImageId() : null,
                    review != null ? review.getAfterImageId() : null
            );
        }).toList();
        return new PageImpl<>(rows, sortedPageable, taskPage.getTotalElements());
    }

    private void validateReviewImage(Long imageId, SprayTask sprayTask, String label) {
        if (imageId == null) {
            return;
        }
        InspectionImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException(label + "不存在: " + imageId));
        if (!Objects.equals(image.getAreaId(), sprayTask.getAreaId())) {
            throw new IllegalArgumentException(label + "不属于点喷任务目标区域: " + imageId);
        }
    }

    private Pageable withDefaultSort(Pageable pageable) {
        return withDefaultSort(pageable, NEWEST_FIRST);
    }

    private Pageable withDefaultSort(Pageable pageable, Sort defaultSort) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }
}
