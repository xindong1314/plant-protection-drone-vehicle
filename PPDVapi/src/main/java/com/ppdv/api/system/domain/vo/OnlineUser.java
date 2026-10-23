package com.ppdv.api.system.domain.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 在线用户会话行（由 Redis 中的 LoginUser 会话转换而来）。
 */
@Getter
@Setter
public class OnlineUser {

    /** 会话编号（登录标识 uuid） */
    private String tokenId;
    /** 登录名称 */
    private String userName;
    /** 所属部门 */
    private String deptName;
    /** 主机地址 */
    private String ipaddr;
    /** 登录地点 */
    private String loginLocation;
    /** 操作系统 */
    private String os;
    /** 浏览器 */
    private String browser;
    /** 登录时间（毫秒时间戳） */
    private Long loginTime;
}
