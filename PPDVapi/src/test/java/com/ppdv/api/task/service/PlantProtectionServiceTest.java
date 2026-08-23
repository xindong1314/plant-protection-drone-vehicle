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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantProtectionServiceTest {

    @Mock SprayTaskRepository sprayRepository;
    @Mock ReviewRecordRepository reviewRepository;
    @Mock ArchiveService archiveService;
    @Mock AreaService areaService;
    @Mock InspectionImageRepository imageRepository;
    @Mock DeviceService deviceService;
    @Mock CommandService commandService;

    private PlantProtectionService service;

    @BeforeEach
    void setUp() {
        service = new PlantProtectionService(sprayRepository, reviewRepository, archiveService, areaService,
                imageRepository, deviceService, commandService);
    }

    @Test
    void repeatedCompletedStatusDoesNotRepeatSideEffects() {
        SprayTask task = task("IN_PROGRESS");
        when(sprayRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(sprayRepository.save(task)).thenReturn(task);

        service.updateSprayTaskStatus(1L, "COMPLETED");
        service.updateSprayTaskStatus(1L, "COMPLETED");

        assertEquals("COMPLETED", task.getStatus());
        verify(reviewRepository, times(1)).save(any(ReviewRecord.class));
        verify(archiveService, times(1)).touchSpray(9L);
        verify(areaService, times(1)).updateRiskStatus(9L, "SPRAYED_PENDING_REVIEW");
    }

    @Test
    void rejectsInvalidOrOutOfOrderStatusChanges() {
        SprayTask task = task("PENDING");
        when(sprayRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateSprayTaskStatus(1L, "UNKNOWN"));
        assertThrows(IllegalStateException.class,
                () -> service.updateSprayTaskStatus(1L, "COMPLETED"));
        verify(sprayRepository, never()).save(any());
    }

    @Test
    void createsTaskOnlyForHighRiskAreaWithoutActiveConflict() {
        GreenhouseArea area = area(9L, "A1", "HIGH_RISK");
        SprayTask task = new SprayTask();
        task.setId(99L);
        task.setAreaId(9L);
        task.setDeviceId("forged-device");
        task.setStatus("COMPLETED");
        when(areaService.getForUpdate(9L)).thenReturn(area);
        when(sprayRepository.existsByAreaIdAndStatusIn(9L,
                Set.of("PENDING", "ASSIGNED", "IN_PROGRESS"))).thenReturn(false);
        when(sprayRepository.save(task)).thenReturn(task);

        SprayTask saved = service.createSprayTask(task);

        assertEquals("PENDING", saved.getStatus());
        assertEquals(null, saved.getId());
        assertEquals(null, saved.getDeviceId());
    }

    @Test
    void rejectsTaskForNormalAreaOrWhenActiveTaskExists() {
        SprayTask task = task("PENDING");
        when(areaService.getForUpdate(9L)).thenReturn(area(9L, "A1", "NORMAL"));
        assertThrows(IllegalStateException.class, () -> service.createSprayTask(task));

        when(areaService.getForUpdate(9L)).thenReturn(area(9L, "A1", "HIGH_RISK"));
        when(sprayRepository.existsByAreaIdAndStatusIn(9L, Set.of("PENDING", "ASSIGNED", "IN_PROGRESS")))
                .thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.createSprayTask(task));
        verify(sprayRepository, never()).save(any());
    }

    @Test
    void assignRejectsUnknownDevice() {
        SprayTask task = task("PENDING");
        when(sprayRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(deviceService.get("missing")).thenThrow(new IllegalArgumentException("设备不存在"));

        assertThrows(IllegalArgumentException.class, () -> service.assignSprayTask(1L, "missing"));
        verify(sprayRepository, never()).save(any());
    }

    @Test
    void assignRejectsOfflineDevice() {
        SprayTask task = task("PENDING");
        Device device = new Device();
        device.setDeviceId("car-1");
        device.setStatus(DeviceStatus.OFFLINE);
        when(sprayRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(deviceService.get("car-1")).thenReturn(device);

        assertThrows(IllegalStateException.class,
                () -> service.assignSprayTask(1L, "car-1"));
        verify(sprayRepository, never()).save(any());
    }

    @Test
    void executeUsesPersistedAreaCodeAndMovesOnlineDeviceTaskToInProgress() {
        SprayTask task = task("ASSIGNED");
        task.setDeviceId("car-1");
        Device device = new Device();
        device.setDeviceId("car-1");
        device.setStatus(DeviceStatus.ONLINE);
        when(sprayRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(areaService.get(9L)).thenReturn(area(9L, "A1", "HIGH_RISK"));
        when(deviceService.get("car-1")).thenReturn(device);
        when(commandService.sendSpray("car-1", "start", "A1", 30)).thenReturn("msg-1");
        when(sprayRepository.save(task)).thenReturn(task);

        String msgId = service.executeSprayTask(1L, "start", 30);

        assertEquals("msg-1", msgId);
        assertEquals("IN_PROGRESS", task.getStatus());
        verify(commandService).sendSpray("car-1", "start", "A1", 30);
    }

    @Test
    void executeRejectsOfflineDeviceWithoutPublishingOrChangingStatus() {
        SprayTask task = task("ASSIGNED");
        task.setDeviceId("car-1");
        Device device = new Device();
        device.setStatus(DeviceStatus.OFFLINE);
        when(sprayRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(areaService.get(9L)).thenReturn(area(9L, "A1", "HIGH_RISK"));
        when(deviceService.get("car-1")).thenReturn(device);

        assertThrows(IllegalStateException.class, () -> service.executeSprayTask(1L, "start", 30));
        assertEquals("ASSIGNED", task.getStatus());
        verify(commandService, never()).sendSpray(any(), any(), any(), any());
        verify(sprayRepository, never()).save(any());
    }

    @Test
    void reviewRejectsMissingOrForeignAreaImage() {
        SprayTask task = task("COMPLETED");
        ReviewRecord review = new ReviewRecord();
        review.setSprayTaskId(1L);
        review.setAreaId(9L);
        review.setBeforeImageId(3L);
        when(sprayRepository.findById(1L)).thenReturn(Optional.of(task));
        InspectionImage image = new InspectionImage();
        image.setId(3L);
        image.setAreaId(10L);
        when(imageRepository.findById(3L)).thenReturn(Optional.of(image));

        assertThrows(IllegalArgumentException.class, () -> service.createReview(review));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void validReviewDerivesAreaFromCompletedTask() {
        SprayTask task = task("COMPLETED");
        ReviewRecord review = new ReviewRecord();
        review.setSprayTaskId(1L);
        review.setResult("PASSED");
        InspectionImage image = new InspectionImage();
        image.setId(3L);
        image.setAreaId(9L);
        review.setAfterImageId(3L);
        when(sprayRepository.findById(1L)).thenReturn(Optional.of(task));
        when(imageRepository.findById(3L)).thenReturn(Optional.of(image));
        when(reviewRepository.save(review)).thenReturn(review);

        ReviewRecord saved = service.createReview(review);

        assertEquals(9L, saved.getAreaId());
        verify(areaService).updateRiskStatus(9L, "NORMAL");
    }

    @Test
    void reviewBoardBatchesLatestReviewRecordsForCompletedTaskPage() {
        SprayTask first = task("COMPLETED");
        first.setId(1L);
        SprayTask second = task("COMPLETED");
        second.setId(2L);
        second.setAreaId(10L);
        PageRequest request = PageRequest.of(0, 10);
        when(sprayRepository.findByStatus(org.mockito.ArgumentMatchers.eq("COMPLETED"), any()))
                .thenReturn(new PageImpl<>(List.of(first, second), request, 2));

        ReviewRecord older = review(1L, "NEED_RETRY", 11L);
        ReviewRecord latest = review(1L, "PASSED", 12L);
        when(reviewRepository.findBySprayTaskIdInOrderByReviewTimeDescIdDesc(List.of(1L, 2L)))
                .thenReturn(List.of(latest, older));

        Page<ReviewTaskView> result = service.listReviewTasks(request);

        assertEquals(2, result.getTotalElements());
        assertEquals("PASSED", result.getContent().get(0).result());
        assertEquals(12L, result.getContent().get(0).afterImageId());
        assertEquals("NEED_RETRY", result.getContent().get(1).result());
        verify(reviewRepository).findBySprayTaskIdInOrderByReviewTimeDescIdDesc(List.of(1L, 2L));
    }

    private SprayTask task(String status) {
        SprayTask task = new SprayTask();
        task.setId(1L);
        task.setAreaId(9L);
        task.setStatus(status);
        return task;
    }

    private GreenhouseArea area(Long id, String code, String riskStatus) {
        GreenhouseArea area = new GreenhouseArea();
        area.setId(id);
        area.setAreaCode(code);
        area.setRiskStatus(riskStatus);
        return area;
    }

    private ReviewRecord review(Long sprayTaskId, String result, Long afterImageId) {
        ReviewRecord review = new ReviewRecord();
        review.setSprayTaskId(sprayTaskId);
        review.setResult(result);
        review.setAfterImageId(afterImageId);
        return review;
    }
}
