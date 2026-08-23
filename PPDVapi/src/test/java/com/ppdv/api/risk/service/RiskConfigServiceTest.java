package com.ppdv.api.risk.service;

import com.ppdv.api.risk.domain.RiskConfig;
import com.ppdv.api.risk.repository.RiskConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class RiskConfigServiceTest {

    @Mock RiskConfigRepository repository;

    private RiskConfigService service;

    @BeforeEach
    void setUp() {
        service = new RiskConfigService(repository);
    }

    @Test
    void updateParsesAndNormalizesValidInteger() {
        RiskConfig config = config(1L, "risk.score.pest", "45");
        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(config));
        when(repository.save(config)).thenReturn(config);

        RiskConfig saved = service.update(1L, " 50 ");

        assertEquals("50", saved.getConfigValue());
        verify(repository).save(config);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1.5", "abc", "2147483648", "-2147483649", "101", "-1"})
    void updateRejectsInvalidDetectionScore(String value) {
        RiskConfig config = config(1L, "risk.score.pest", "45");
        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(config));

        assertThrows(IllegalArgumentException.class, () -> service.update(1L, value));
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsSuspectedThresholdNotLowerThanHighThreshold() {
        RiskConfig suspected = config(1L, "risk.threshold.suspected", "21");
        RiskConfig high = config(2L, "risk.threshold.high", "51");
        when(repository.findById(1L)).thenReturn(Optional.of(suspected));
        when(repository.findThresholdsForUpdate()).thenReturn(java.util.List.of(high, suspected));

        assertThrows(IllegalArgumentException.class, () -> service.update(1L, "51"));
        verify(repository).findThresholdsForUpdate();
        verify(repository, never()).save(any());
    }

    @Test
    void validThresholdUpdateLocksBothRowsBeforeSaving() {
        RiskConfig suspected = config(1L, "risk.threshold.suspected", "21");
        RiskConfig high = config(2L, "risk.threshold.high", "51");
        when(repository.findById(1L)).thenReturn(Optional.of(suspected));
        when(repository.findThresholdsForUpdate()).thenReturn(java.util.List.of(high, suspected));
        when(repository.save(suspected)).thenReturn(suspected);

        RiskConfig saved = service.update(1L, "30");

        assertEquals("30", saved.getConfigValue());
        var order = inOrder(repository);
        order.verify(repository).findThresholdsForUpdate();
        order.verify(repository).save(suspected);
    }

    @Test
    void updateRejectsHighThresholdNotGreaterThanSuspectedThreshold() {
        RiskConfig suspected = config(1L, "risk.threshold.suspected", "21");
        RiskConfig high = config(2L, "risk.threshold.high", "51");
        when(repository.findById(2L)).thenReturn(Optional.of(high));
        when(repository.findThresholdsForUpdate()).thenReturn(java.util.List.of(high, suspected));

        assertThrows(IllegalArgumentException.class, () -> service.update(2L, "21"));
        verify(repository).findThresholdsForUpdate();
        verify(repository, never()).save(any());
    }

    @Test
    void getIntDoesNotSilentlyHideCorruptStoredValue() {
        RiskConfig config = config(1L, "risk.score.pest", "2147483648");
        when(repository.findByConfigKey("risk.score.pest")).thenReturn(Optional.of(config));

        assertThrows(IllegalArgumentException.class,
                () -> service.getInt("risk.score.pest", 45));
    }

    private RiskConfig config(Long id, String key, String value) {
        RiskConfig config = new RiskConfig();
        config.setId(id);
        config.setConfigKey(key);
        config.setConfigValue(value);
        return config;
    }
}
