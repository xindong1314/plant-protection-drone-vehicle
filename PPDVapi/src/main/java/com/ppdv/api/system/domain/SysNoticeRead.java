package com.ppdv.api.system.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 公告已读记录（sys_notice_read），notice_id + user_id 幂等去重。
 */
@Entity
@Table(name = "sys_notice_read")
@Getter
@Setter
public class SysNoticeRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long readId;

    /** 公告 ID（sys_notice.notice_id） */
    private Long noticeId;

    /** 阅读用户 ID（当前登录人 userId） */
    private Long userId;

    /** 阅读时间 */
    private LocalDateTime readTime;
}
