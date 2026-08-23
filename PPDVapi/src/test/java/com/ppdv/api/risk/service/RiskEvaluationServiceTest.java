package com.ppdv.api.risk.service;

import com.ppdv.api.device.DeviceRepository;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.service.AreaService;
import com.ppdv.api.inspection.domain.DetectionResult;
import com.ppdv.api.inspection.repository.DetectionResultRepository;
import com.ppdv.api.task.repository.SprayTaskRepository;
import com.ppdv.api.telemetry.TelemetryTsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskEvaluationServiceTest {

    @Mock RiskConfigService configService;
    @Mock AreaService areaService;
    @Mock DetectionResultRepository detectionRepository;
    @Mock SprayTaskRepository sprayTaskRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock ObjectProvider<TelemetryTsService> telemetryProvider;

    private RiskEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new RiskEvaluationService(configService, areaService, detectionRepository,
                sprayTaskRepository, deviceRepository, telemetryProvider);
    }

    @Test
    void ignoredDetectionDoesNotContributeToCurrentOrHistoryRisk() {
        GreenhouseArea area = new GreenhouseArea();
        area.setId(5L);
        area.setAreaCode("A1");
        area.setAreaName("A1 区");
        DetectionResult ignored = new DetectionResult();
        ignored.setAreaId(5L);
        ignored.setCategory("虫害");
        ignored.setConfidence(1.0);
        ignored.setStatus("IGNORED");
        when(areaService.get(5L)).thenReturn(area);
        when(detectionRepository.findByAreaIdOrderByIdDesc(5L)).thenReturn(List.of(ignored));
        when(configService.getInt("risk.score.detection_max", 80)).thenReturn(80);
        when(configService.getInt("risk.threshold.suspected", 21)).thenReturn(21);
        when(configService.getInt("risk.threshold.high", 51)).thenReturn(51);

        Map<String, Object> result = service.evaluate(5L);

        assertEquals(0, result.get("detectionScore"));
        assertEquals(0, result.get("historyScore"));
        assertEquals("NORMAL", result.get("status"));
        verify(configService, never()).getInt("risk.score.pest", 0);
        verify(areaService).updateRiskScoreAndStatus(5L, 0, "NORMAL");
    }
}
