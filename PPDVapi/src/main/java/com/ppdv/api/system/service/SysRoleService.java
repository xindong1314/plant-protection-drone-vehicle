package com.ppdv.api.system.service;

import com.ppdv.api.common.exception.ServiceException;
import com.ppdv.api.system.domain.SysRole;
import com.ppdv.api.system.domain.SysRoleDept;
import com.ppdv.api.system.domain.SysRoleMenu;
import com.ppdv.api.system.domain.SysUserRole;
import com.ppdv.api.system.repository.SysRoleDeptRepository;
import com.ppdv.api.system.repository.SysRoleMenuRepository;
import com.ppdv.api.system.repository.SysRoleRepository;
import com.ppdv.api.system.repository.SysUserRoleRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色服务：角色 CRUD、角色-菜单授权、用户角色查询。
 */
@Service
public class SysRoleService {

    private final SysRoleRepository roleRepository;
    private final SysRoleMenuRepository roleMenuRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysRoleDeptRepository roleDeptRepository;

    public SysRoleService(SysRoleRepository roleRepository,
                          SysRoleMenuRepository roleMenuRepository,
                          SysUserRoleRepository userRoleRepository,
                          SysRoleDeptRepository roleDeptRepository) {
        this.roleRepository = roleRepository;
        this.roleMenuRepository = roleMenuRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleDeptRepository = roleDeptRepository;
    }

    public List<SysRole> selectRolesByUserId(Long userId) {
        return roleRepository.selectRolesByUserId(userId);
    }

    public Set<String> selectRoleKeysByUserId(Long userId) {
        return selectRolesByUserId(userId).stream().map(SysRole::getRoleKey).collect(Collectors.toSet());
    }

    public Page<SysRole> selectRolePage(SysRole filter, Pageable pageable) {
        return roleRepository.findAll(buildSpec(filter), pageable);
    }

    public List<SysRole> selectRoleList(SysRole filter) {
        return roleRepository.findAll(buildSpec(filter));
    }

    public List<SysRole> selectRoleAll() {
        return roleRepository.findByDelFlagOrderByRoleSortAsc("0");
    }

    private Specification<SysRole> buildSpec(SysRole filter) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("delFlag"), "0"));
            if (filter != null) {
                if (StringUtils.hasText(filter.getRoleName())) {
                    ps.add(cb.like(root.get("roleName"), "%" + filter.getRoleName() + "%"));
                }
                if (StringUtils.hasText(filter.getRoleKey())) {
                    ps.add(cb.like(root.get("roleKey"), "%" + filter.getRoleKey() + "%"));
                }
                if (StringUtils.hasText(filter.getStatus())) {
                    ps.add(cb.equal(root.get("status"), filter.getStatus()));
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    public SysRole selectRoleById(Long roleId) {
        return roleRepository.findById(roleId).orElse(null);
    }

    public boolean checkRoleNameUnique(SysRole role) {
        boolean exists = roleRepository.existsByRoleNameAndDelFlag(role.getRoleName(), "0");
        if (!exists) {
            return true;
        }
        // 名称已存在，若为自身则允许
        return roleRepository.findAll(buildSpec(new SysRole())).stream()
                .noneMatch(r -> r.getRoleName().equals(role.getRoleName()) && !r.getRoleId().equals(role.getRoleId()));
    }

    public boolean checkRoleKeyUnique(SysRole role) {
        boolean exists = roleRepository.existsByRoleKeyAndDelFlag(role.getRoleKey(), "0");
        if (!exists) {
            return true;
        }
        return roleRepository.findAll(buildSpec(new SysRole())).stream()
                .noneMatch(r -> r.getRoleKey().equals(role.getRoleKey()) && !r.getRoleId().equals(role.getRoleId()));
    }

    @Transactional
    public SysRole insertRole(SysRole role) {
        if (role.getDelFlag() == null) {
            role.setDelFlag("0");
        }
        role.setCreateTime(LocalDateTime.now());
        SysRole saved = roleRepository.save(role);
        insertRoleMenu(saved.getRoleId(), role.getMenuIds());
        return saved;
    }

    @Transactional
    public SysRole updateRole(SysRole role) {
        role.setUpdateTime(LocalDateTime.now());
        SysRole saved = roleRepository.save(role);
        roleMenuRepository.deleteByRoleId(role.getRoleId());
        insertRoleMenu(role.getRoleId(), role.getMenuIds());
        return saved;
    }

    @Transactional
    public void updateRoleStatus(SysRole role) {
        SysRole db = selectRoleById(role.getRoleId());
        if (db == null) {
            throw new ServiceException("角色不存在");
        }
        db.setStatus(role.getStatus());
        db.setUpdateTime(LocalDateTime.now());
        roleRepository.save(db);
    }

    private void insertRoleMenu(Long roleId, Long[] menuIds) {
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                roleMenuRepository.save(new SysRoleMenu(roleId, menuId));
            }
        }
    }

    @Transactional
    public void deleteRoleByIds(Long[] roleIds) {
        for (Long roleId : roleIds) {
            if (roleId == 1L) {
                throw new ServiceException("超级管理员角色不能删除");
            }
            if (userRoleRepository.countByRoleId(roleId) > 0) {
                SysRole r = selectRoleById(roleId);
                throw new ServiceException((r != null ? r.getRoleName() : "该角色") + "已分配，不能删除");
            }
        }
        for (Long roleId : roleIds) {
            roleMenuRepository.deleteByRoleId(roleId);
            roleDeptRepository.deleteByRoleId(roleId);
            // 逻辑删除
            SysRole role = selectRoleById(roleId);
            if (role != null) {
                role.setDelFlag("2");
                roleRepository.save(role);
            }
        }
    }

    /** 分配数据权限：更新数据范围与自定义部门。 */
    @Transactional
    public void authDataScope(SysRole role) {
        SysRole db = selectRoleById(role.getRoleId());
        if (db == null) {
            throw new ServiceException("角色不存在");
        }
        db.setDataScope(role.getDataScope());
        db.setDeptCheckStrictly(role.getDeptCheckStrictly());
        db.setUpdateTime(LocalDateTime.now());
        roleRepository.save(db);
        roleDeptRepository.deleteByRoleId(role.getRoleId());
        if (role.getDeptIds() != null) {
            for (Long deptId : role.getDeptIds()) {
                roleDeptRepository.save(new SysRoleDept(role.getRoleId(), deptId));
            }
        }
    }

    /** 取消授权某用户 */
    @Transactional
    public void deleteAuthUser(Long roleId, Long userId) {
        userRoleRepository.deleteByRoleIdAndUserIds(roleId, List.of(userId));
    }

    /** 批量取消授权 */
    @Transactional
    public void deleteAuthUsers(Long roleId, Long[] userIds) {
        userRoleRepository.deleteByRoleIdAndUserIds(roleId, Arrays.asList(userIds));
    }

    /** 批量授权用户 */
    @Transactional
    public void insertAuthUsers(Long roleId, Long[] userIds) {
        if (userIds != null) {
            for (Long userId : userIds) {
                userRoleRepository.save(new SysUserRole(userId, roleId));
            }
        }
    }
}
