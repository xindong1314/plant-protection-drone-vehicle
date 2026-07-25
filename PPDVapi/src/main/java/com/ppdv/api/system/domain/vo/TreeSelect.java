package com.ppdv.api.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ppdv.api.system.domain.SysDept;
import com.ppdv.api.system.domain.SysMenu;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 树形下拉（部门树 / 菜单树）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TreeSelect {

    private Long id;
    private String label;
    private List<TreeSelect> children;

    public TreeSelect() {
    }

    public TreeSelect(SysMenu menu) {
        this.id = menu.getMenuId();
        this.label = menu.getMenuName();
        this.children = menu.getChildren().stream().map(TreeSelect::new).collect(Collectors.toList());
    }

    public TreeSelect(SysDept dept) {
        this.id = dept.getDeptId();
        this.label = dept.getDeptName();
        this.children = dept.getChildren().stream().map(TreeSelect::new).collect(Collectors.toList());
    }
}
