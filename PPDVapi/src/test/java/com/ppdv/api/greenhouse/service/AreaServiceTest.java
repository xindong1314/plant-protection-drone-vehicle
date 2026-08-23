package com.ppdv.api.greenhouse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppdv.api.archive.service.ArchiveService;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.repository.AreaRepository;
import com.ppdv.api.inspection.repository.DetectionResultRepository;
import com.ppdv.api.inspection.repository.InspectionImageRepository;
import com.ppdv.api.inspection.repository.InspectionRecordRepository;
import com.ppdv.api.task.repository.ReviewRecordRepository;
import com.ppdv.api.task.repository.SprayTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AreaServiceTest {

    @Mock AreaRepository repository;
    @Mock ArchiveService archiveService;
    @Mock InspectionRecordRepository inspectionRepository;
    @Mock InspectionImageRepository imageRepository;
    @Mock DetectionResultRepository detectionRepository;
    @Mock SprayTaskRepository sprayRepository;
    @Mock ReviewRecordRepository reviewRepository;

    private AreaService service;

    @BeforeEach
    void setUp() {
        service = new AreaService(repository, archiveService, inspectionRepository, imageRepository,
                detectionRepository, sprayRepository, reviewRepository, new ObjectMapper());
    }

    @Test
    void createPersistsAreaAndInitialArchiveInSameServiceOperation() {
        GreenhouseArea area = area("C1");
        area.setAreaCode("  C1  ");
        area.setRiskStatus("HIGH_RISK");
        area.setRiskScore(99);
        area.setLastEvalAt(LocalDateTime.now());
        area.setBoundaryJson("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]");
        when(repository.save(area)).thenAnswer(invocation -> {
            area.setId(7L);
            return area;
        });

        GreenhouseArea saved = service.create(area);

        assertEquals(7L, saved.getId());
        assertEquals("NORMAL", saved.getRiskStatus());
        assertEquals(0, saved.getRiskScore());
        assertNull(saved.getLastEvalAt());
        assertEquals("C1", saved.getAreaCode());
        assertEquals("GCJ-02", saved.getCoordinateSystem());
        verify(archiveService).createForArea(7L, "NORMAL", "tester");
    }

    @Test
    void updateWithoutArucoIdKeepsExistingBinding() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setArucoId("ARUCO-7");
        db.setCoordinateSystem("GCJ-02");
        db.setRiskStatus("HIGH_RISK");
        db.setRiskScore(88);
        GreenhouseArea request = area("C1");
        request.setId(7L);
        request.setRiskStatus("NORMAL");
        request.setRiskScore(0);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(repository.save(db)).thenReturn(db);

        GreenhouseArea saved = service.update(request);

        assertEquals("ARUCO-7", saved.getArucoId());
        assertEquals("HIGH_RISK", saved.getRiskStatus());
        assertEquals(88, saved.getRiskScore());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "{}",
            "[]",
            "[[116,39],[117,39]]",
            "[[181,39],[117,39],[117,40]]",
            "[[116,91],[117,39],[117,40]]",
            "[[116,39,1],[117,39],[117,40]]",
            "[[116,39],[116,39],[117,40]]",
            "[[116,39],[117,40],[118,41]]"
    })
    void createRejectsMalformedOrOutOfRangeBoundary(String boundaryJson) {
        GreenhouseArea area = area("C1");
        area.setBoundaryJson(boundaryJson);

        assertThrows(IllegalArgumentException.class, () -> service.create(area));
        verify(repository, never()).save(any());
        verify(archiveService, never()).createForArea(any(), any(), any());
    }

    @Test
    void createRejectsUnsupportedCoordinateSystem() {
        GreenhouseArea area = area("C1");
        area.setCoordinateSystem("WGS84");

        assertThrows(IllegalArgumentException.class, () -> service.create(area));
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsBlankAreaCode() {
        GreenhouseArea area = area("C1");
        area.setAreaCode("   ");

        assertThrows(IllegalArgumentException.class, () -> service.create(area));
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsDuplicateAreaCode() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        GreenhouseArea request = area("C2");
        request.setId(7L);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(repository.existsByAreaCodeAndIdNot("C2", 7L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.update(request));
        verify(repository, never()).save(any());
    }

    @Test
    void deleteRejectsAreaWithBusinessHistory() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(inspectionRepository.existsByAreaId(7L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.delete(7L));
        verify(archiveService, never()).deleteForArea(7L);
        verify(repository, never()).delete(any(GreenhouseArea.class));
    }

    @Test
    void deleteRemovesOwnedArchiveBeforeUnreferencedArea() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));

        service.delete(7L);

        InOrder order = inOrder(archiveService, repository);
        order.verify(archiveService).deleteForArea(7L);
        order.verify(repository).delete(db);
    }

    private GreenhouseArea area(String code) {
        GreenhouseArea area = new GreenhouseArea();
        area.setAreaCode(code);
        area.setAreaName(code + " 区");
        area.setGreenhouseName("一号棚");
        area.setCreateBy("tester");
        return area;
    }
}
