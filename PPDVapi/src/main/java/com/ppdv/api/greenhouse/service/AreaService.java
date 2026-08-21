package com.ppdv.api.greenhouse.service;

import com.ppdv.api.archive.service.ArchiveService;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.repository.AreaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AreaService {

    private final AreaRepository repository;
    private final ArchiveService archiveService;

    public AreaService(AreaRepository repository, ArchiveService archiveService) {
        this.repository = repository;
        this.archiveService = archiveService;
    }

    @Transactional(readOnly = true)
    public List<GreenhouseArea> list() {
        return repository.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public GreenhouseArea get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("区域不存在: " + id));
    }

    @Transactional(readOnly = true)
    public GreenhouseArea getByCode(String areaCode) {
        return repository.findByAreaCode(areaCode)
                .orElseThrow(() -> new IllegalArgumentException("区域不存在: " + areaCode));
    }

    @Transactional
    public GreenhouseArea create(GreenhouseArea area) {
        if (repository.existsByAreaCode(area.getAreaCode())) {
            throw new IllegalStateException("区域编号已存在: " + area.getAreaCode());
        }
        if (area.getRiskStatus() == null) {
            area.setRiskStatus("NORMAL");
        }
        if (area.getSortOrder() == null) {
            area.setSortOrder(0);
        }
        if (area.getCoordinateSystem() == null || area.getCoordinateSystem().isBlank()) {
            area.setCoordinateSystem("GCJ-02");
        }
        return repository.save(area);
    }

    @Transactional
    public GreenhouseArea update(GreenhouseArea area) {
        GreenhouseArea db = get(area.getId());
        db.setAreaCode(area.getAreaCode());
        db.setAreaName(area.getAreaName());
        db.setGreenhouseName(area.getGreenhouseName());
        db.setArucoId(area.getArucoId());
        db.setBoundaryJson(area.getBoundaryJson());
        if (area.getCoordinateSystem() != null && !area.getCoordinateSystem().isBlank()) {
            db.setCoordinateSystem(area.getCoordinateSystem());
        }
        if (area.getRiskStatus() != null) {
            db.setRiskStatus(area.getRiskStatus());
        }
        if (area.getSortOrder() != null) {
            db.setSortOrder(area.getSortOrder());
        }
        db.setRemark(area.getRemark());
        return repository.save(db);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public void updateRiskStatus(Long areaId, String riskStatus) {
        GreenhouseArea area = get(areaId);
        area.setRiskStatus(riskStatus);
        repository.save(area);
        archiveService.updateRiskStatus(areaId, riskStatus);
    }

    /** 更新风险评分 + 状态 + 评估时间。 */
    @Transactional
    public void updateRiskScoreAndStatus(Long areaId, int score, String status) {
        GreenhouseArea area = get(areaId);
        area.setRiskScore(score);
        area.setRiskStatus(status);
        area.setLastEvalAt(LocalDateTime.now());
        repository.save(area);
        archiveService.updateRiskStatus(areaId, status);
    }
}
