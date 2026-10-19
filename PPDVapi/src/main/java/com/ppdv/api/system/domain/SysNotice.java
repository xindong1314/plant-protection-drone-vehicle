package com.ppdv.api.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 通知公告（sys_notice）。
 */
@Entity
@Table(name = "sys_notice")
@Getter
@Setter
public class SysNotice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long noticeId;

    /** 公告标题 */
    private String noticeTitle;

    /** 公告类型（1 通知 2 公告） */
    private String noticeType;

    /** 公告内容（富文本，长文本） */
    @Column(columnDefinition = "LONGTEXT")
    private String noticeContent;

    /** 公告状态（0 正常 1 关闭） */
    private String status;
}
