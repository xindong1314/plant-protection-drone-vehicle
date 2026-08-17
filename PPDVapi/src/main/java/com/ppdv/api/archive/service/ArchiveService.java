package com.ppdv.api.archive.service;

import com.ppdv.api.archive.domain.GreenhouseArchive;
import com.ppdv.api.archive.repository.ArchiveRepository;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.repository.AreaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArchiveService {

    private final ArchiveRepository archiveRepository;
    private final AreaRepository areaRepository;

    public ArchiveService(ArchiveRepository archiveRepository, AreaRepository areaRepository) {
        this.archiveRepository = archiveRepository;
        this.areaRepository = areaRepository;
    }

    @Transactional(readOnly = true)
    public GreenhouseArchive getByArea(Long areaId) {
        return archiveRepository.findByAreaId(areaId)
                .orElseThrow(() -> new IllegalArgumentException("区域档案不存在: " + areaId));
    }

    @Transactional(readOnly = true)
    public List<GreenhouseArchive> listAll() {
        return archiveRepository.findAll();
    }

    /** 数字地图聚合：返回所有区域 + 档案汇总。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDigitalMap() {
        List<GreenhouseArea> areas = areaRepository.findAllByOrderBySortOrderAsc();
        return areas.stream().map(area -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", area.getId());
            entry.put("areaCode", area.getAreaCode());
            entry.put("areaName", area.getAreaName());
            entry.put("greenhouseName", area.getGreenhouseName());
            entry.put("riskStatus", area.getRiskStatus());
            entry.put("arucoId", area.getArucoId());

            GreenhouseArchive archive = archiveRepository.findByAreaId(area.getId()).orElse(null);
            if (archive != null) {
                entry.put("totalInspections", archive.getTotalInspections());
                entry.put("totalAnomalies", archive.getTotalAnomalies());
                entry.put("totalSprays", archive.getTotalSprays());
                entry.put("lastInspectionTime", archive.getLastInspectionTime());
                entry.put("lastRiskStatus", archive.getLastRiskStatus());
            }
            return entry;
        }).toList();
    }

    /** 更新区域档案（巡检/异常/点喷触发时调用）。 */
    @Transactional
    public GreenhouseArchive touchInspection(Long areaId) {
        GreenhouseArchive archive = archiveRepository.findByAreaId(areaId).orElseGet(() -> {
            GreenhouseArchive a = new GreenhouseArchive();
            a.setAreaId(areaId);
            a.setTotalInspections(0);
            a.setTotalAnomalies(0);
            a.setTotalSprays(0);
            a.setLastRiskStatus("NORMAL");
            return a;
        });
        archive.setTotalInspections(archive.getTotalInspections() + 1);
        archive.setLastInspectionTime(LocalDateTime.now());
        return archiveRepository.save(archive);
    }

    @Transactional
    public GreenhouseArchive touchAnomaly(Long areaId, int count) {
        GreenhouseArchive archive = getByArea(areaId);
        archive.setTotalAnomalies(archive.getTotalAnomalies() + count);
        archive.setLastAnomalyTime(LocalDateTime.now());
        return archiveRepository.save(archive);
    }

    @Transactional
    public GreenhouseArchive touchSpray(Long areaId) {
        GreenhouseArchive archive = getByArea(areaId);
        archive.setTotalSprays(archive.getTotalSprays() + 1);
        archive.setLastSprayTime(LocalDateTime.now());
        return archiveRepository.save(archive);
    }

    @Transactional
    public void updateRiskStatus(Long areaId, String riskStatus) {
        GreenhouseArchive archive = getByArea(areaId);
        archive.setLastRiskStatus(riskStatus);
        archiveRepository.save(archive);
    }
}
