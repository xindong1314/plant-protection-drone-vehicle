package com.ppdv.api.track.domain;

import com.ppdv.api.system.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 跟踪会话：一次巡检/跟踪过程（车端 trackSessionId 为批键）。
 * 唯一键 (device_id, session_key)，见 V14。
 */
@Entity
@Table(name = "coer_track_session")
@Getter
@Setter
public class TrackSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    private Long areaId;
    private String sessionKey;

    /** 会话开始：会话内最早 firstSeenTs（tracks=[] 时取消息 ts）；后到更早的 firstSeenTs 会回溯修正。 */
    private Long startTs;

    /** 会话结束：车端 sessionEnd 取消息 ts、云端空闲超时取服务器侧最后活动时间；idle 结束的会话复归 ACTIVE 时清空。 */
    private Long endTs;

    /** 结束来源：session_end=车端 sessionEnd（不可复归）/ idle=云端空闲超时（新活动可复归）；ACTIVE 时为 null。 */
    private String endReason;

    /** 服务器侧最后活动时间（apply 时刷新）：空闲清扫只按服务器时钟判定，车端时钟超前不影响。 */
    private Long lastActivityTs;

    /** ACTIVE / ENDED。 */
    private String status;
}
