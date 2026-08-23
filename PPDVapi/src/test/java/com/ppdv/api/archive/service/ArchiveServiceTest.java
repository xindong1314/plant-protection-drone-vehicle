package com.ppdv.api.archive.service;

import com.ppdv.api.archive.domain.GreenhouseArchive;
import com.ppdv.api.archive.repository.ArchiveRepository;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.repository.AreaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {

    @Mock ArchiveRepository archiveRepository;
    @Mock AreaRepository areaRepository;

    private ArchiveService service;

    @BeforeEach
    void setUp() {
        service = new ArchiveService(archiveRepository, areaRepository);
    }

    @Test
    void digitalMapLoadsArchivesInOneBatchAndIncludesLatestTimes() {
        GreenhouseArea first = area(1L, "A1");
        GreenhouseArea second = area(2L, "A2");
        GreenhouseArchive archive = new GreenhouseArchive();
        archive.setAreaId(1L);
        archive.setTotalInspections(3);
        archive.setTotalAnomalies(2);
        archive.setTotalSprays(1);
        LocalDateTime anomalyTime = LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime sprayTime = LocalDateTime.of(2026, 8, 21, 11, 0);
        archive.setLastAnomalyTime(anomalyTime);
        archive.setLastSprayTime(sprayTime);
        when(areaRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(first, second));
        when(archiveRepository.findByAreaIdIn(List.of(1L, 2L))).thenReturn(List.of(archive));

        List<Map<String, Object>> rows = service.getDigitalMap();

        assertEquals(2, rows.size());
        assertEquals(3, rows.get(0).get("totalInspections"));
        assertEquals(anomalyTime, rows.get(0).get("lastAnomalyTime"));
        assertEquals(sprayTime, rows.get(0).get("lastSprayTime"));
        verify(archiveRepository).findByAreaIdIn(List.of(1L, 2L));
        verify(archiveRepository, never()).findByAreaId(any());
    }

    @Test
    void digitalMapSkipsArchiveQueryWhenThereAreNoAreas() {
        when(areaRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        assertEquals(List.of(), service.getDigitalMap());

        verify(archiveRepository, never()).findByAreaIdIn(any());
    }

    @Test
    void touchInspectionLocksArchiveBeforeIncrementingCounter() {
        GreenhouseArchive archive = new GreenhouseArchive();
        archive.setAreaId(1L);
        archive.setTotalInspections(1);
        when(archiveRepository.findByAreaIdForUpdate(1L)).thenReturn(java.util.Optional.of(archive));
        when(archiveRepository.save(archive)).thenReturn(archive);

        service.touchInspection(1L);
        service.touchInspection(1L);

        assertEquals(3, archive.getTotalInspections());
        verify(archiveRepository, times(2)).findByAreaIdForUpdate(1L);
    }

    private GreenhouseArea area(Long id, String code) {
        GreenhouseArea area = new GreenhouseArea();
        area.setId(id);
        area.setAreaCode(code);
        area.setAreaName(code + " 区");
        area.setRiskStatus("NORMAL");
        return area;
    }
}
