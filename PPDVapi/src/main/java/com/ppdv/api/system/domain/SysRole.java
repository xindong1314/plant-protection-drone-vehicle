package com.ppdv.api.system.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sys_role")
@Getter
@Setter
public class SysRole extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roleId;

    private String roleName;
    private String roleKey;
    private Integer roleSort;
    private String dataScope;
    private Boolean menuCheckStrictly;
    private Boolean deptCheckStrictly;
    private String status;
    private String delFlag;

    /** 前端菜单树勾选 id 集合 */
    @Transient
    private Long[] menuIds;
    @Transient
    private Long[] deptIds;

    public boolean isAdmin() {
        return roleId != null && roleId == 1L;
    }
}
