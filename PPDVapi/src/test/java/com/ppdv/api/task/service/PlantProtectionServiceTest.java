package com.ppdv.api.task.service;

import com.ppdv.api.archive.service.ArchiveService;
import com.ppdv.api.greenhouse.service.AreaService;
import com.ppdv.api.task.domain.ReviewRecord;
import com.ppdv.api.task.domain.SprayTask;
import com.ppdv.api.task.repository.ReviewRecordRepository;
import com.ppdv.api.task.repository.SprayTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

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

    private PlantProtectionService service;

    @BeforeEach
    void setUp() {
        service = new PlantProtectionService(sprayRepository, reviewRepository, archiveService, areaService);
    }

    @Test
    void repeatedCompletedStatusDoesNotRepeatSideEffects() {
        SprayTask task = task("IN_PROGRESS");
        when(sprayRepository.findById(1L)).thenReturn(Optional.of(task));
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
        when(sprayRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateSprayTaskStatus(1L, "UNKNOWN"));
        assertThrows(IllegalStateException.class,
                () -> service.updateSprayTaskStatus(1L, "COMPLETED"));
        verify(sprayRepository, never()).save(any());
    }

    private SprayTask task(String status) {
        SprayTask task = new SprayTask();
        task.setId(1L);
        task.setAreaId(9L);
        task.setStatus(status);
        return task;
    }
}
