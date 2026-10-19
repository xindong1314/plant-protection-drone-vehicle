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
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /** 为新区域创建一份空档案。与区域创建共用事务，任一步失败都会整体回滚。 */
    @Transactional
    public GreenhouseArchive createForArea(Long areaId, String riskStatus, String createBy) {
        if (archiveRepository.existsByAreaId(areaId)) {
            throw new IllegalStateException("区域档案已存在: " + areaId);
        }
        GreenhouseArchive archive = new GreenhouseArchive();
        archive.setAreaId(areaId);
        archive.setTotalInspections(0);
        archive.setTotalAnomalies(0);
        archive.setTotalSprays(0);
        archive.setLastRiskStatus(riskStatus);
        archive.setCreateBy(createBy);
        return archiveRepository.save(archive);
    }

    /** 仅供删除无业务历史的区域时清理其一对一档案。 */
    @Transactional
    public void deleteForArea(Long areaId) {
        archiveRepository.deleteByAreaId(areaId);
    }

    /** 数字地图聚合：返回所有区域 + 档案汇总。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDigitalMap() {
        return getDigitalMap(null, null);
    }

    /** 数字地图聚合（带过滤）：keyword 模糊匹配区域编号/名称/大棚，riskStatus 精确匹配当前风险。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDigitalMap(String keyword, String riskStatus) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase();
        String normalizedRisk = riskStatus == null || riskStatus.isBlank() ? null : riskStatus.trim().toUpperCase();
        List<GreenhouseArea> areas = areaRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(area -> normalizedRisk == null || normalizedRisk.equals(area.getRiskStatus()))
                .filter(area -> normalizedKeyword == null || matchesAreaKeyword(area, normalizedKeyword))
                .toList();
        List<Long> areaIds = areas.stream().map(GreenhouseArea::getId).toList();
        Map<Long, GreenhouseArchive> archivesByArea = areaIds.isEmpty()
                ? Map.of()
                : archiveRepository.findByAreaIdIn(areaIds).stream()
                        .collect(Collectors.toMap(GreenhouseArchive::getAreaId, Function.identity()));
        return areas.stream().map(area -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", area.getId());
            entry.put("areaCode", area.getAreaCode());
            entry.put("areaName", area.getAreaName());
            entry.put("greenhouseName", area.getGreenhouseName());
            entry.put("riskStatus", area.getRiskStatus());
            entry.put("arucoId", area.getArucoId());

            GreenhouseArchive archive = archivesByArea.get(area.getId());
            if (archive != null) {
                entry.put("totalInspections", archive.getTotalInspections());
                entry.put("totalAnomalies", archive.getTotalAnomalies());
                entry.put("totalSprays", archive.getTotalSprays());
                entry.put("lastInspectionTime", archive.getLastInspectionTime());
                entry.put("lastAnomalyTime", archive.getLastAnomalyTime());
                entry.put("lastSprayTime", archive.getLastSprayTime());
                entry.put("lastRiskStatus", archive.getLastRiskStatus());
            }
            return entry;
        }).toList();
    }

    private boolean matchesAreaKeyword(GreenhouseArea area, String lowerKeyword) {
        return containsIgnoreCase(area.getAreaCode(), lowerKeyword)
                || containsIgnoreCase(area.getAreaName(), lowerKeyword)
                || containsIgnoreCase(area.getGreenhouseName(), lowerKeyword);
    }

    private boolean containsIgnoreCase(String text, String lowerKeyword) {
        return text != null && text.toLowerCase().contains(lowerKeyword);
    }

    /** 更新区域档案（巡检/异常/点喷触发时调用）。 */
    @Transactional
    public GreenhouseArchive touchInspection(Long areaId) {
        GreenhouseArchive archive = archiveRepository.findByAreaIdForUpdate(areaId).orElseGet(() -> {
            GreenhouseArchive a = new GreenhouseArchive();
            a.setAreaId(areaId);
            a.setTotalInspections(0);
            a.setTotalAnomalies(0);
            a.setTotalSprays(0);
            a.setLastRiskStatus("NORMAL");
            return a;
        });
        archive.setTotalInspections(valueOrZero(archive.getTotalInspections()) + 1);
        archive.setLastInspectionTime(LocalDateTime.now());
        return archiveRepository.save(archive);
    }

    @Transactional
    public GreenhouseArchive touchAnomaly(Long areaId, int count) {
        GreenhouseArchive archive = getByAreaForUpdate(areaId);
        archive.setTotalAnomalies(valueOrZero(archive.getTotalAnomalies()) + count);
        archive.setLastAnomalyTime(LocalDateTime.now());
        return archiveRepository.save(archive);
    }

    @Transactional
    public GreenhouseArchive touchSpray(Long areaId) {
        GreenhouseArchive archive = getByAreaForUpdate(areaId);
        archive.setTotalSprays(valueOrZero(archive.getTotalSprays()) + 1);
        archive.setLastSprayTime(LocalDateTime.now());
        return archiveRepository.save(archive);
    }

    @Transactional
    public void updateRiskStatus(Long areaId, String riskStatus) {
        GreenhouseArchive archive = getByAreaForUpdate(areaId);
        archive.setLastRiskStatus(riskStatus);
        archiveRepository.save(archive);
    }

    private GreenhouseArchive getByAreaForUpdate(Long areaId) {
        return archiveRepository.findByAreaIdForUpdate(areaId)
                .orElseThrow(() -> new IllegalArgumentException("区域档案不存在: " + areaId));
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
