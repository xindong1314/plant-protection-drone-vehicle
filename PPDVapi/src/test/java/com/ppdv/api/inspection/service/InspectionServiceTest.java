package com.ppdv.api.inspection.service;

import com.ppdv.api.archive.service.ArchiveService;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock InspectionRecordRepository recordRepository;
    @Mock InspectionImageRepository imageRepository;
    @Mock DetectionResultRepository detectionRepository;
    @Mock ArchiveService archiveService;
    @Mock RiskEvaluationService riskEvaluationService;

    private InspectionService service;

    @BeforeEach
    void setUp() {
        service = new InspectionService(recordRepository, imageRepository, detectionRepository,
                archiveService, riskEvaluationService);
    }

    @Test
    void persistsDetectionBeforeRiskEvaluationAndUpdatesCounters() {
        DetectionResult detection = new DetectionResult();
        detection.setImageId(3L);
        detection.setCategory("虫害");

        InspectionImage image = new InspectionImage();
        image.setId(3L);
        image.setInspectionId(4L);
        image.setAreaId(5L);

        InspectionRecord record = new InspectionRecord();
        record.setId(4L);
        record.setAreaId(5L);
        record.setAnomalyCount(0);

        when(imageRepository.findById(3L)).thenReturn(Optional.of(image));
        when(detectionRepository.saveAllAndFlush(List.of(detection))).thenReturn(List.of(detection));
        when(recordRepository.findById(4L)).thenReturn(Optional.of(record));

        List<DetectionResult> saved = service.addDetections(List.of(detection));

        assertEquals(5L, saved.get(0).getAreaId());
        assertEquals(1, record.getAnomalyCount());
        InOrder order = inOrder(detectionRepository, riskEvaluationService);
        order.verify(detectionRepository).saveAllAndFlush(List.of(detection));
        order.verify(riskEvaluationService).evaluate(5L);
        verify(archiveService).touchAnomaly(5L, 1);
    }
}
