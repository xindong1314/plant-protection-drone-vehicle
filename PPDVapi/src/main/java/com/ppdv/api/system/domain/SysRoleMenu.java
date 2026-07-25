package com.ppdv.api.system.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "sys_role_menu")
@IdClass(SysRoleMenu.Pk.class)
@Getter
@Setter
public class SysRoleMenu {

    @Id
    private Long roleId;
    @Id
    private Long menuId;

    public SysRoleMenu() {
    }

    public SysRoleMenu(Long roleId, Long menuId) {
        this.roleId = roleId;
        this.menuId = menuId;
    }

    public static class Pk implements Serializable {
        private Long roleId;
        private Long menuId;

        public Pk() {
        }

        public Pk(Long roleId, Long menuId) {
            this.roleId = roleId;
            this.menuId = menuId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(roleId, pk.roleId) && Objects.equals(menuId, pk.menuId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleId, menuId);
        }
    }
}
