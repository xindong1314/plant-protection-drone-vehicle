package com.ppdv.api.system.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "sys_user")
@Getter
@Setter
public class SysUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    private Long deptId;
    private String userName;
    private String nickName;
    private String userType;
    private String email;
    private String phonenumber;
    private String sex;
    private String avatar;

    /** 仅接收入参，响应中不输出 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String status;
    private String delFlag;
    private String loginIp;
    private LocalDateTime loginDate;

    @Transient
    private SysDept dept;
    @Transient
    private List<SysRole> roles;
    @Transient
    private Long[] roleIds;
    @Transient
    private Long[] postIds;
    @Transient
    private Long roleId;

    public boolean isAdmin() {
        return userId != null && userId == 1L;
    }
}
