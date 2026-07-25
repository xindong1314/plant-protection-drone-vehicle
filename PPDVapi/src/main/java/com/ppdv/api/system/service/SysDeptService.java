package com.ppdv.api.system.service;

import com.ppdv.api.common.exception.ServiceException;
import com.ppdv.api.system.domain.SysDept;
import com.ppdv.api.system.domain.vo.TreeSelect;
import com.ppdv.api.system.repository.SysDeptRepository;
import com.ppdv.api.system.repository.SysRoleDeptRepository;
import com.ppdv.api.system.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 部门服务：部门树、CRUD、角色部门授权查询。
 */
@Service
public class SysDeptService {

    private final SysDeptRepository deptRepository;
    private final SysUserRepository userRepository;
    private final SysRoleDeptRepository roleDeptRepository;

    public SysDeptService(SysDeptRepository deptRepository,
                          SysUserRepository userRepository,
                          SysRoleDeptRepository roleDeptRepository) {
        this.deptRepository = deptRepository;
        this.userRepository = userRepository;
        this.roleDeptRepository = roleDeptRepository;
    }

    public List<SysDept> selectDeptList(SysDept filter) {
        return deptRepository.findByDelFlagOrderByParentIdAscOrderNumAsc("0").stream()
                .filter(d -> filter == null || !StringUtils.hasText(filter.getDeptName())
                        || d.getDeptName().contains(filter.getDeptName()))
                .filter(d -> filter == null || !StringUtils.hasText(filter.getStatus())
                        || filter.getStatus().equals(d.getStatus()))
                .collect(Collectors.toList());
    }

    public List<SysDept> selectDeptListExcludeChild(Long excludeId) {
        return deptRepository.findByDelFlagOrderByParentIdAscOrderNumAsc("0").stream()
                .filter(d -> !d.getDeptId().equals(excludeId))
                .filter(d -> d.getAncestors() == null
                        || !List.of(d.getAncestors().split(",")).contains(String.valueOf(excludeId)))
                .collect(Collectors.toList());
    }

    public List<TreeSelect> buildDeptTreeSelect(List<SysDept> depts) {
        return buildDeptTree(depts).stream().map(TreeSelect::new).collect(Collectors.toList());
    }

    public List<SysDept> buildDeptTree(List<SysDept> depts) {
        List<SysDept> returnList = new ArrayList<>();
        List<Long> ids = depts.stream().map(SysDept::getDeptId).collect(Collectors.toList());
        for (SysDept dept : depts) {
            if (!ids.contains(dept.getParentId())) {
                recursionFn(depts, dept);
                returnList.add(dept);
            }
        }
        return returnList.isEmpty() ? depts : returnList;
    }

    private void recursionFn(List<SysDept> list, SysDept node) {
        List<SysDept> children = list.stream()
                .filter(d -> node.getDeptId().equals(d.getParentId()))
                .collect(Collectors.toList());
        node.setChildren(children);
        for (SysDept child : children) {
            recursionFn(list, child);
        }
    }

    public SysDept selectDeptById(Long deptId) {
        return deptRepository.findById(deptId).orElse(null);
    }

    public List<Long> selectDeptIdsByRoleId(Long roleId) {
        return roleDeptRepository.selectDeptIdsByRoleId(roleId);
    }

    public boolean hasChildByDeptId(Long deptId) {
        return deptRepository.countByParentIdAndDelFlag(deptId, "0") > 0;
    }

    public boolean checkDeptExistUser(Long deptId) {
        return userRepository.countByDeptIdAndDelFlag(deptId, "0") > 0;
    }

    public boolean checkDeptNameUnique(SysDept dept) {
        return deptRepository.findByParentIdAndDelFlag(dept.getParentId(), "0").stream()
                .noneMatch(d -> d.getDeptName().equals(dept.getDeptName())
                        && !d.getDeptId().equals(dept.getDeptId()));
    }

    public SysDept insertDept(SysDept dept) {
        SysDept parent = dept.getParentId() == null ? null : selectDeptById(dept.getParentId());
        if (parent != null && !"0".equals(parent.getStatus())) {
            throw new ServiceException("上级部门停用，不允许新增");
        }
        String ancestors = parent == null ? "0" : parent.getAncestors() + "," + dept.getParentId();
        dept.setAncestors(ancestors);
        if (dept.getDelFlag() == null) {
            dept.setDelFlag("0");
        }
        dept.setCreateTime(LocalDateTime.now());
        return deptRepository.save(dept);
    }

    public SysDept updateDept(SysDept dept) {
        SysDept parent = dept.getParentId() == null ? null : selectDeptById(dept.getParentId());
        if (parent != null) {
            dept.setAncestors(parent.getAncestors() + "," + dept.getParentId());
        }
        dept.setUpdateTime(LocalDateTime.now());
        return deptRepository.save(dept);
    }

    public void deleteDeptById(Long deptId) {
        SysDept dept = selectDeptById(deptId);
        if (dept != null) {
            dept.setDelFlag("2");
            deptRepository.save(dept);
        }
    }
}
