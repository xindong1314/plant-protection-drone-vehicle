package com.ppdv.api.track.domain;

import com.ppdv.api.system.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 跟踪观测：目标一次出现（episode）的结构化记录（非逐帧检测）。
 * 唯一键 (device_id, track_session_id, episode_key)，见 V14；track_key 仅展示/关联。
 */
@Entity
@Table(name = "coer_detection_track")
@Getter
@Setter
public class DetectionTrack extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;

    /** 逻辑外键 → coer_track_session.id（不做 DB 外键）。 */
    private Long trackSessionId;

    private Long areaId;

    /** 车端 trackId（仅展示/关联，不保证跨出现稳定）。 */
    private String trackKey;

    /** 一次出现ID（身份键）：同出现内稳定，再次出现必须换新。 */
    private String episodeKey;

    /** 同 trackId 第几次出现（从 0 递增，仅展示/诊断）。 */
    private Integer episodeNo;

    /** Q1 规范 class key；未知类落 __unknown__。 */
    private String category;

    private Double confidence;

    /** 本次出现首帧时间（episode 内保持不变）。 */
    private Long firstSeenTs;
    /** 本次出现末帧/最新帧时间（episode 结束后定格）。 */
    private Long lastSeenTs;

    @Column(name = "box_x")
    private Integer boxX;

    @Column(name = "box_y")
    private Integer boxY;

    @Column(name = "box_w")
    private Integer boxW;

    @Column(name = "box_h")
    private Integer boxH;

    private Integer imageWidth;
    private Integer imageHeight;

    /** 抽稀轨迹 JSON：[[x,y,ts],...]。 */
    @Column(name = "trajectory_json")
    private String trajectoryJson;

    /** ACTIVE / LOST / ENDED / REAPPEAR（ENDED=本次出现结束，不结束巡检会话）。 */
    private String trackState;

    /** 0–4，车端估算；无则 null。 */
    private Integer severity;
    private String severitySource;

    /** 由 firstSeenTs 转换，便于报表按时间排序。 */
    private LocalDateTime capturedAt;
}
