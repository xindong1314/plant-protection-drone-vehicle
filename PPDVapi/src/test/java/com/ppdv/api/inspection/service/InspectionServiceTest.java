package com.ppdv.api.inspection.service;

import com.ppdv.api.archive.service.ArchiveService;
import com.ppdv.api.device.Device;
import com.ppdv.api.device.DeviceService;
import com.ppdv.api.device.DeviceStatus;
import com.ppdv.api.greenhouse.service.AreaService;
import com.ppdv.api.inspection.domain.DetectionResult;
import com.ppdv.api.inspection.domain.InspectionImage;
import com.ppdv.api.inspection.domain.InspectionRecord;
import com.ppdv.api.inspection.repository.DetectionResultRepository;
import com.ppdv.api.inspection.repository.InspectionImageRepository;
import com.ppdv.api.inspection.repository.InspectionRecordRepository;
import com.ppdv.api.risk.service.RiskEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock InspectionRecordRepository recordRepository;
    @Mock InspectionImageRepository imageRepository;
    @Mock DetectionResultRepository detectionRepository;
    @Mock ArchiveService archiveService;
    @Mock RiskEvaluationService riskEvaluationService;
    @Mock DeviceService deviceService;
    @Mock AreaService areaService;

    private InspectionService service;

    @BeforeEach
    void setUp() {
        service = new InspectionService(recordRepository, imageRepository, detectionRepository,
                archiveService, riskEvaluationService, deviceService, areaService);
    }

    @Test
    void persistsDetectionBeforeRiskEvaluationAndUpdatesCounters() {
        DetectionResult detection = new DetectionResult();
        detection.setImageId(3L);
        detection.setCategory("虫害");
        detection.setConfidence(0.8);
        detection.setStatus("PENDING");

        InspectionImage image = new InspectionImage();
        image.setId(3L);
        image.setInspectionId(4L);
        image.setAreaId(5L);

        InspectionRecord record = new InspectionRecord();
        record.setId(4L);
        record.setAreaId(5L);
        record.setAnomalyCount(0);

        when(imageRepository.findAllById(any())).thenReturn(List.of(image));
        when(detectionRepository.saveAllAndFlush(List.of(detection))).thenReturn(List.of(detection));
        when(recordRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(record));

        List<DetectionResult> saved = service.addDetections(List.of(detection));

        assertEquals(5L, saved.get(0).getAreaId());
        assertEquals(1, record.getAnomalyCount());
        InOrder order = inOrder(detectionRepository, riskEvaluationService);
        order.verify(detectionRepository).saveAllAndFlush(List.of(detection));
        order.verify(riskEvaluationService).evaluate(5L);
        verify(archiveService).touchAnomaly(5L, 1);
    }

    @Test
    void startInspectionRejectsOfflineDevice() {
        Device device = new Device();
        device.setDeviceId("car-1");
        device.setStatus(DeviceStatus.OFFLINE);
        when(deviceService.get("car-1")).thenReturn(device);

        assertThrows(IllegalStateException.class,
                () -> service.startInspection("car-1", 5L));
        verify(areaService, never()).getForUpdate(any());
        verify(recordRepository, never()).save(any());
    }

    @Test
    void addImageDerivesAreaFromInProgressInspection() {
        InspectionRecord record = new InspectionRecord();
        record.setId(4L);
        record.setAreaId(5L);
        record.setStatus("IN_PROGRESS");
        record.setImageCount(0);
        when(recordRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(record));
        when(imageRepository.save(any(InspectionImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InspectionImage saved = service.addImage(4L, null, "/uploads/a.jpg");

        assertEquals(5L, saved.getAreaId());
        assertEquals(1, record.getImageCount());
    }

    @Test
    void addImageRejectsCompletedInspectionAndMismatchedArea() {
        InspectionRecord record = new InspectionRecord();
        record.setId(4L);
        record.setAreaId(5L);
        record.setStatus("COMPLETED");
        when(recordRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class,
                () -> service.addImage(4L, 5L, "/uploads/a.jpg"));

        record.setStatus("IN_PROGRESS");
        assertThrows(IllegalArgumentException.class,
                () -> service.addImage(4L, 6L, "/uploads/a.jpg"));
        verify(imageRepository, never()).save(any());
    }

    @Test
    void addDetectionsRejectsMissingImageAndCrossAreaSpoofing() {
        DetectionResult missing = detection(99L, null, 0.8, "PENDING");
        when(imageRepository.findAllById(any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> service.addDetections(List.of(missing)));

        InspectionImage image = new InspectionImage();
        image.setId(3L);
        image.setAreaId(5L);
        DetectionResult spoofed = detection(3L, 6L, 0.8, "PENDING");
        when(imageRepository.findAllById(any())).thenReturn(List.of(image));
        assertThrows(IllegalArgumentException.class,
                () -> service.addDetections(List.of(spoofed)));
        verify(detectionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void addDetectionsRejectsInvalidConfidenceAndStatus() {
        DetectionResult badConfidence = detection(3L, null, 1.1, "PENDING");
        assertThrows(IllegalArgumentException.class,
                () -> service.addDetections(List.of(badConfidence)));

        DetectionResult badStatus = detection(3L, null, 0.8, "DONE");
        assertThrows(IllegalArgumentException.class,
                () -> service.addDetections(List.of(badStatus)));
        verify(imageRepository, never()).findAllById(any());
    }

    @Test
    void addDetectionRejectsNullWithBusinessError() {
        assertThrows(IllegalArgumentException.class, () -> service.addDetection(null));
        verify(detectionRepository, never()).save(any());
    }

    @Test
    void repeatedCompleteInspectionTouchesArchiveOnlyOnce() {
        InspectionRecord record = new InspectionRecord();
        record.setId(4L);
        record.setAreaId(5L);
        record.setStatus("IN_PROGRESS");
        when(recordRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(record));
        when(recordRepository.save(record)).thenReturn(record);

        service.completeInspection(4L);
        service.completeInspection(4L);

        assertEquals("COMPLETED", record.getStatus());
        verify(recordRepository, times(2)).findByIdForUpdate(4L);
        verify(recordRepository, times(1)).save(record);
        verify(archiveService, times(1)).touchInspection(5L);
    }

    private DetectionResult detection(Long imageId, Long areaId, Double confidence, String status) {
        DetectionResult result = new DetectionResult();
        result.setImageId(imageId);
        result.setAreaId(areaId);
        result.setCategory("虫害");
        result.setConfidence(confidence);
        result.setStatus(status);
        return result;
    }
}
