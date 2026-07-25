package com.ppdv.api.system.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sys_menu")
@Getter
@Setter
public class SysMenu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long menuId;

    private String menuName;
    private Long parentId;
    private Integer orderNum;
    private String path;
    private String component;
    private String query;
    /** 是否外链（0是 1否） */
    private String isFrame;
    /** 是否缓存（0缓存 1不缓存） */
    private String isCache;
    /** 菜单类型（M目录 C菜单 F按钮） */
    private String menuType;
    private String visible;
    private String status;
    private String perms;
    private String icon;

    @Transient
    private List<SysMenu> children = new ArrayList<>();

    public List<SysMenu> getChildren() {
        return children;
    }
}
