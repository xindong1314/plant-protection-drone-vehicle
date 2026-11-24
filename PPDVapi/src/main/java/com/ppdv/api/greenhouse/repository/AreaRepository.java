package com.ppdv.api.greenhouse.repository;

import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.dto.AreaDigest;
import com.ppdv.api.greenhouse.dto.AreaSummary;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AreaRepository extends JpaRepository<GreenhouseArea, Long>, JpaSpecificationExecutor<GreenhouseArea> {

    Optional<GreenhouseArea> findByAreaCode(String areaCode);

    boolean existsByAreaCode(String areaCode);

    boolean existsByAreaCodeAndIdNot(String areaCode, Long id);

    List<GreenhouseArea> findAllByOrderBySortOrderAsc();

    /**
     * 区域列表摘要投影（v1.1 评审 M1）：不携带 route_json 全文，只给点数与录入时间，
     * 避免巡检/点喷/复查/规划等只需下拉的页面全量下载路线。
     * routePointCount 读冗余列（评审修复#11），不再对最长 512KB 的 route_json 逐行 JSON_LENGTH。
     */
    @Query(value = """
            SELECT a.id AS id,
                   a.area_code AS areaCode,
                   a.area_name AS areaName,
                   a.greenhouse_name AS greenhouseName,
                   a.aruco_id AS arucoId,
                   a.remark AS remark,
                   a.boundary_json AS boundaryJson,
                   a.coordinate_system AS coordinateSystem,
                   a.sort_order AS sortOrder,
                   a.risk_status AS riskStatus,
                   a.risk_score AS riskScore,
                   a.last_eval_at AS lastEvalAt,
                   IFNULL(a.route_point_count, 0) AS routePointCount,
                   a.route_recorded_at AS routeRecordedAt
            FROM coer_greenhouse_area a
            ORDER BY a.sort_order ASC
            """, nativeQuery = true)
    List<AreaSummary> findAllSummaries();

    /** 全部已录路线的区域（route_json 非空）：批量路线接口专用（评审修复#11）。 */
    @Query("select a from GreenhouseArea a where a.routeJson is not null order by a.sortOrder asc")
    List<GreenhouseArea> findAllWithRoute();

    /** 数字地图聚合的轻量投影（评审修复#11）：聚合输出不含边界/路线，避免全实体加载大文本列。 */
    @Query("""
            select new com.ppdv.api.greenhouse.dto.AreaDigest(a.id, a.areaCode, a.areaName,
                    a.greenhouseName, a.arucoId, a.riskStatus)
            from GreenhouseArea a
            order by a.sortOrder asc
            """)
    List<AreaDigest> findAllDigests();

    /** 按关键字模糊匹配区域编号/名称，返回命中的区域 ID，供巡检、点喷等列表按区域条件过滤。 */
    @Query("select a.id from GreenhouseArea a where a.areaCode like :pattern or a.areaName like :pattern")
    List<Long> findIdsByKeywordPattern(@Param("pattern") String pattern);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GreenhouseArea a where a.id = :id")
    Optional<GreenhouseArea> findByIdForUpdate(@Param("id") Long id);
}
