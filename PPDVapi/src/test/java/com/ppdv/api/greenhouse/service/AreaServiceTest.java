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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertEquals("CGCS2000", saved.getCoordinateSystem());
        verify(archiveService).createForArea(7L, "NORMAL", "tester");
    }

    @Test
    void updateWithoutArucoIdKeepsExistingBinding() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setArucoId("ARUCO-7");
        db.setCoordinateSystem("CGCS2000");
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

    @ParameterizedTest
    @ValueSource(strings = {"WGS84", "BD-09"})
    void createRejectsUnsupportedCoordinateSystem(String coordinateSystem) {
        GreenhouseArea area = area("C1");
        area.setCoordinateSystem(coordinateSystem);

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

    @Test
    void createClearsClientSuppliedRouteFieldsWithoutBoundary() {
        GreenhouseArea area = area("C1");
        area.setRouteJson("[[116.0,39.0],[116.1,39.1]]");
        area.setRoutePointCount(99);
        area.setRouteRecordedAt(LocalDateTime.now());
        when(repository.save(area)).thenAnswer(invocation -> {
            area.setId(8L);
            return area;
        });

        GreenhouseArea saved = service.create(area);

        assertNull(saved.getRouteJson());
        assertNull(saved.getRoutePointCount());
        assertNull(saved.getRouteRecordedAt());
        assertEquals("CGCS2000", saved.getCoordinateSystem());
        verify(archiveService).createForArea(8L, "NORMAL", "tester");
    }

    @Test
    void updateKeepsExistingRouteFields() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setRouteJson("[[116.0,39.0],[116.1,39.1]]");
        db.setRoutePointCount(5);
        db.setRouteRecordedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        GreenhouseArea request = area("C1");
        request.setId(7L);
        request.setRouteJson("[[1,1],[2,2]]");
        request.setRoutePointCount(99);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(repository.save(db)).thenReturn(db);

        GreenhouseArea saved = service.update(request);

        assertEquals("[[116.0,39.0],[116.1,39.1]]", saved.getRouteJson());
        assertEquals(5, saved.getRoutePointCount());
        assertEquals(LocalDateTime.of(2026, 1, 1, 8, 0), saved.getRouteRecordedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "[[116,39]]",
            "[[116,39,1],[117,40]]",
            "[[181,39],[117,40]]"
    })
    void saveRecordedRouteRejectsInvalidRoute(String routeJson) {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setCoordinateSystem("CGCS2000");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));

        assertThrows(IllegalArgumentException.class, () -> service.saveRecordedRoute(7L, routeJson));
        verify(repository, never()).save(any());
        assertNull(db.getRouteJson());
        assertNull(db.getRouteRecordedAt());
    }

    @Test
    void saveRecordedRouteRejectsOversizedRoute() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setCoordinateSystem("CGCS2000");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        StringBuilder route = new StringBuilder("[");
        while (route.length() <= 512 * 1024) {
            route.append("[116.123456,39.123456],");
        }
        route.setLength(route.length() - 1);
        route.append(']');

        assertThrows(IllegalArgumentException.class, () -> service.saveRecordedRoute(7L, route.toString()));
        verify(repository, never()).save(any());
        assertNull(db.getRouteJson());
    }

    @Test
    void saveRecordedRouteRejectsAreaWithoutCgcs2000Boundary() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setCoordinateSystem("GCJ-02");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));

        assertThrows(IllegalArgumentException.class,
                () -> service.saveRecordedRoute(7L, "[[116.0,39.0],[116.1,39.1]]"));
        verify(repository, never()).save(any());
        assertNull(db.getRouteJson());
        assertNull(db.getRouteRecordedAt());
    }

    @Test
    void saveRecordedRoutePersistsRouteAndRecordedAt() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setCoordinateSystem("CGCS2000");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(repository.save(db)).thenReturn(db);

        service.saveRecordedRoute(7L, "[[116.0,39.0],[116.1,39.1],[116.0,39.0]]");

        assertEquals("[[116.0,39.0],[116.1,39.1],[116.0,39.0]]", db.getRouteJson());
        assertEquals(3, db.getRoutePointCount());
        assertNotNull(db.getRouteRecordedAt());
        verify(repository).save(db);
    }

    @Test
    void updateRejectsCoordinateSystemChangeWithoutRedrawingBoundary() {
        // v1.1 评审 S1：旧 GCJ-02 区域“编辑但不重画”不得被静默改标为 CGCS2000。
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setBoundaryJson("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]");
        db.setCoordinateSystem("GCJ-02");
        GreenhouseArea request = area("C1");
        request.setId(7L);
        request.setBoundaryJson(null);
        request.setCoordinateSystem("CGCS2000");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));

        assertThrows(IllegalArgumentException.class, () -> service.update(request));
        verify(repository, never()).save(any());
        assertEquals("GCJ-02", db.getCoordinateSystem());
        assertNotNull(db.getBoundaryJson());
    }

    @Test
    void updateAllowsCoordinateSystemChangeWhenBoundaryRedrawn() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setBoundaryJson("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]");
        db.setCoordinateSystem("GCJ-02");
        GreenhouseArea request = area("C1");
        request.setId(7L);
        request.setBoundaryJson("[[116.2,39.1],[116.3,39.1],[116.3,39.2]]");
        request.setCoordinateSystem("CGCS2000");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(repository.save(db)).thenReturn(db);

        GreenhouseArea saved = service.update(request);

        assertEquals("CGCS2000", saved.getCoordinateSystem());
        verify(repository).save(db);
    }

    @Test
    void updateClearsBoundaryWithEmptyStringAndResetsCoordinateSystem() {
        // v1.1 评审 L7：boundaryJson 传空字符串表示显式清除边界，坐标系归位 CGCS2000。
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setBoundaryJson("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]");
        db.setCoordinateSystem("GCJ-02");
        GreenhouseArea request = area("C1");
        request.setId(7L);
        request.setBoundaryJson("");
        request.setCoordinateSystem("CGCS2000");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(repository.save(db)).thenReturn(db);

        GreenhouseArea saved = service.update(request);

        assertNull(saved.getBoundaryJson());
        assertEquals("CGCS2000", saved.getCoordinateSystem());
        verify(repository).save(db);
    }

    @Test
    void updateWithNewBoundaryAndMissingCoordinateSystemDefaultsToCgcs2000() {
        // 评审修复#8：带新边界但未声明坐标系时补默认 CGCS2000，新边界不得沿用旧 GCJ-02 标签。
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setBoundaryJson("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]");
        db.setCoordinateSystem("GCJ-02");
        GreenhouseArea request = area("C1");
        request.setId(7L);
        request.setBoundaryJson("[[116.2,39.1],[116.3,39.1],[116.3,39.2]]");
        request.setCoordinateSystem(null);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(repository.save(db)).thenReturn(db);

        GreenhouseArea saved = service.update(request);

        assertEquals("CGCS2000", saved.getCoordinateSystem());
        verify(repository).save(db);
    }

    @Test
    void updateRejectsResubmittingIdenticalBoundaryWithChangedCoordinateSystem() {
        // 评审修复#8：原样回传库内旧边界 + 声称 CGCS2000 视同未重画，S1 守卫同样拒绝。
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setBoundaryJson("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]");
        db.setCoordinateSystem("GCJ-02");
        GreenhouseArea request = area("C1");
        request.setId(7L);
        request.setBoundaryJson("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]");
        request.setCoordinateSystem("CGCS2000");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));

        assertThrows(IllegalArgumentException.class, () -> service.update(request));
        verify(repository, never()).save(any());
        assertEquals("GCJ-02", db.getCoordinateSystem());
        assertEquals("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]", db.getBoundaryJson());
    }

    @Test
    void updateWithUnchangedBoundaryAndBlankCoordinateSystemKeepsExistingLabel() {
        // 评审修复#8：未真正重画的边界 + 未声明坐标系时保持库内坐标系不变，
        // 默认补 CGCS2000 只适用于新边界，否则等于从空坐标系一侧绕过 S1 守卫。
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setBoundaryJson("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]");
        db.setCoordinateSystem("GCJ-02");
        GreenhouseArea request = area("C1");
        request.setId(7L);
        request.setBoundaryJson("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]");
        request.setCoordinateSystem(null);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(repository.save(db)).thenReturn(db);

        GreenhouseArea saved = service.update(request);

        assertEquals("GCJ-02", saved.getCoordinateSystem());
        verify(repository).save(db);
    }

    private GreenhouseArea area(String code) {
        GreenhouseArea area = new GreenhouseArea();
        area.setAreaCode(code);
        area.setAreaName(code + " 区");
        area.setGreenhouseName("一号棚");
        area.setCreateBy("tester");
        return area;
    }

    @Test
    void updateWithRedrawnBoundaryInvalidatesRecordedRoute() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setBoundaryJson("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]");
        db.setRouteJson("[[116.15,39.15],[116.16,39.16]]");
        db.setRoutePointCount(2);
        db.setRouteRecordedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        GreenhouseArea request = area("C1");
        request.setId(7L);
        request.setBoundaryJson("[[116.2,39.1],[116.3,39.1],[116.3,39.2]]");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(repository.save(db)).thenReturn(db);

        GreenhouseArea saved = service.update(request);

        // 边界重画后旧路线与新几何不再匹配，必须作废，避免规划页返回落在新边界外的路线。
        assertNull(saved.getRouteJson());
        assertNull(saved.getRoutePointCount());
        assertNull(saved.getRouteRecordedAt());
    }

    @Test
    void updateClearingBoundaryInvalidatesRecordedRoute() {
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setBoundaryJson("[[116.1,39.1],[116.2,39.1],[116.2,39.2]]");
        db.setRouteJson("[[116.15,39.15],[116.16,39.16]]");
        db.setRoutePointCount(2);
        db.setRouteRecordedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        GreenhouseArea request = area("C1");
        request.setId(7L);
        request.setBoundaryJson("");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(repository.save(db)).thenReturn(db);

        GreenhouseArea saved = service.update(request);

        assertNull(saved.getRouteJson());
        assertNull(saved.getRoutePointCount());
        assertNull(saved.getRouteRecordedAt());
    }

    @Test
    void updateResubmittingSameBoundaryKeepsRecordedRoute() {
        String boundary = "[[116.1,39.1],[116.2,39.1],[116.2,39.2]]";
        GreenhouseArea db = area("C1");
        db.setId(7L);
        db.setBoundaryJson(boundary);
        db.setRouteJson("[[116.15,39.15],[116.16,39.16]]");
        db.setRoutePointCount(2);
        GreenhouseArea request = area("C1");
        request.setId(7L);
        request.setBoundaryJson(boundary);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(db));
        when(repository.save(db)).thenReturn(db);

        GreenhouseArea saved = service.update(request);

        // 原样回传相同边界（管理端普通编辑）不算重画，路线保留。
        assertEquals("[[116.15,39.15],[116.16,39.16]]", saved.getRouteJson());
        assertEquals(2, saved.getRoutePointCount());
    }
}
