package com.ppdv.api.system.domain.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 公告已读用户行（sys_notice_read 关联 sys_user / sys_dept 的投影）。
 */
@Getter
@Setter
public class SysNoticeReadVo {

    /** 登录名称 */
    private String userName;
    /** 用户名称 */
    private String nickName;
    /** 手机号码 */
    private String phonenumber;
    /** 所属部门 */
    private String deptName;
    /** 阅读时间 */
    private LocalDateTime readTime;

    public SysNoticeReadVo() {
    }

    /** 供 JPQL 构造器表达式使用（字段顺序与投影查询一致）。 */
    public SysNoticeReadVo(String userName, String nickName, String phonenumber, String deptName, LocalDateTime readTime) {
        this.userName = userName;
        this.nickName = nickName;
        this.phonenumber = phonenumber;
        this.deptName = deptName;
        this.readTime = readTime;
    }
}
