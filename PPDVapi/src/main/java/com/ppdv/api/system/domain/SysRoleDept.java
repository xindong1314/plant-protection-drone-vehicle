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
@Table(name = "sys_role_dept")
@IdClass(SysRoleDept.Pk.class)
@Getter
@Setter
public class SysRoleDept {

    @Id
    private Long roleId;
    @Id
    private Long deptId;

    public SysRoleDept() {
    }

    public SysRoleDept(Long roleId, Long deptId) {
        this.roleId = roleId;
        this.deptId = deptId;
    }

    public static class Pk implements Serializable {
        private Long roleId;
        private Long deptId;

        public Pk() {
        }

        public Pk(Long roleId, Long deptId) {
            this.roleId = roleId;
            this.deptId = deptId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(roleId, pk.roleId) && Objects.equals(deptId, pk.deptId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleId, deptId);
        }
    }
}
