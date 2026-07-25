package com.ppdv.api.system.service;

import com.ppdv.api.common.exception.ServiceException;
import com.ppdv.api.security.SecurityUtils;
import com.ppdv.api.system.domain.SysUser;
import com.ppdv.api.system.domain.SysUserPost;
import com.ppdv.api.system.domain.SysUserRole;
import com.ppdv.api.system.repository.SysPostRepository;
import com.ppdv.api.system.repository.SysUserPostRepository;
import com.ppdv.api.system.repository.SysUserRepository;
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
import java.util.List;
import java.util.Optional;

/**
 * 用户服务：用户 CRUD、角色/岗位绑定、密码与状态维护。
 */
@Service
public class SysUserService {

    private final SysUserRepository userRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysUserPostRepository userPostRepository;
    private final SysPostRepository postRepository;

    public SysUserService(SysUserRepository userRepository,
                          SysUserRoleRepository userRoleRepository,
                          SysUserPostRepository userPostRepository,
                          SysPostRepository postRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.userPostRepository = userPostRepository;
        this.postRepository = postRepository;
    }

    public Optional<SysUser> selectUserByUserName(String userName) {
        return userRepository.findByUserNameAndDelFlag(userName, "0");
    }

    public Page<SysUser> selectUserPage(SysUser filter, Pageable pageable) {
        return userRepository.findAll(buildSpec(filter), pageable);
    }

    private Specification<SysUser> buildSpec(SysUser filter) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("delFlag"), "0"));
            if (filter != null) {
                if (StringUtils.hasText(filter.getUserName())) {
                    ps.add(cb.like(root.get("userName"), "%" + filter.getUserName() + "%"));
                }
                if (StringUtils.hasText(filter.getPhonenumber())) {
                    ps.add(cb.like(root.get("phonenumber"), "%" + filter.getPhonenumber() + "%"));
                }
                if (StringUtils.hasText(filter.getStatus())) {
                    ps.add(cb.equal(root.get("status"), filter.getStatus()));
                }
                if (filter.getDeptId() != null) {
                    ps.add(cb.equal(root.get("deptId"), filter.getDeptId()));
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    public SysUser selectUserById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

    public List<Long> selectRoleIdsByUserId(Long userId) {
        return userRoleRepository.findByUserId(userId).stream().map(SysUserRole::getRoleId).toList();
    }

    public List<Long> selectPostIdsByUserId(Long userId) {
        return postRepository.selectPostIdsByUserId(userId);
    }

    public boolean checkUserNameUnique(SysUser user) {
        Optional<SysUser> exist = userRepository.findByUserNameAndDelFlag(user.getUserName(), "0");
        return exist.isEmpty() || exist.get().getUserId().equals(user.getUserId());
    }

    public boolean checkPhoneUnique(SysUser user) {
        if (!StringUtils.hasText(user.getPhonenumber())) {
            return true;
        }
        return !userRepository.existsByPhonenumberAndDelFlag(user.getPhonenumber(), "0")
                || isSelf(user, u -> user.getPhonenumber().equals(u.getPhonenumber()));
    }

    public boolean checkEmailUnique(SysUser user) {
        if (!StringUtils.hasText(user.getEmail())) {
            return true;
        }
        return !userRepository.existsByEmailAndDelFlag(user.getEmail(), "0")
                || isSelf(user, u -> user.getEmail().equals(u.getEmail()));
    }

    private boolean isSelf(SysUser user, java.util.function.Predicate<SysUser> match) {
        return userRepository.findAll(buildSpec(new SysUser())).stream()
                .filter(match)
                .allMatch(u -> u.getUserId().equals(user.getUserId()));
    }

    @Transactional
    public SysUser insertUser(SysUser user) {
        user.setDelFlag("0");
        if (!StringUtils.hasText(user.getStatus())) {
            user.setStatus("0");
        }
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        }
        user.setCreateTime(LocalDateTime.now());
        SysUser saved = userRepository.save(user);
        insertUserRole(saved.getUserId(), user.getRoleIds());
        insertUserPost(saved.getUserId(), user.getPostIds());
        return saved;
    }

    @Transactional
    public SysUser updateUser(SysUser user) {
        SysUser db = selectUserById(user.getUserId());
        if (db == null) {
            throw new ServiceException("用户不存在");
        }
        db.setNickName(user.getNickName());
        db.setDeptId(user.getDeptId());
        db.setPhonenumber(user.getPhonenumber());
        db.setEmail(user.getEmail());
        db.setSex(user.getSex());
        if (StringUtils.hasText(user.getStatus())) {
            db.setStatus(user.getStatus());
        }
        db.setRemark(user.getRemark());
        db.setUpdateTime(LocalDateTime.now());
        SysUser saved = userRepository.save(db);
        userRoleRepository.deleteByUserId(user.getUserId());
        userPostRepository.deleteByUserId(user.getUserId());
        insertUserRole(user.getUserId(), user.getRoleIds());
        insertUserPost(user.getUserId(), user.getPostIds());
        return saved;
    }

    private void insertUserRole(Long userId, Long[] roleIds) {
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                userRoleRepository.save(new SysUserRole(userId, roleId));
            }
        }
    }

    private void insertUserPost(Long userId, Long[] postIds) {
        if (postIds != null) {
            for (Long postId : postIds) {
                userPostRepository.save(new SysUserPost(userId, postId));
            }
        }
    }

    @Transactional
    public void deleteUserByIds(Long[] userIds) {
        for (Long userId : userIds) {
            if (SecurityUtils.isAdmin(userId)) {
                throw new ServiceException("不允许删除超级管理员用户");
            }
        }
        for (Long userId : userIds) {
            userRoleRepository.deleteByUserId(userId);
            userPostRepository.deleteByUserId(userId);
            SysUser user = selectUserById(userId);
            if (user != null) {
                user.setDelFlag("2");
                userRepository.save(user);
            }
        }
    }

    @Transactional
    public void resetPwd(SysUser user) {
        SysUser db = selectUserById(user.getUserId());
        if (db == null) {
            throw new ServiceException("用户不存在");
        }
        db.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        db.setUpdateTime(LocalDateTime.now());
        userRepository.save(db);
    }

    @Transactional
    public void updateUserStatus(SysUser user) {
        SysUser db = selectUserById(user.getUserId());
        if (db == null) {
            throw new ServiceException("用户不存在");
        }
        db.setStatus(user.getStatus());
        db.setUpdateTime(LocalDateTime.now());
        userRepository.save(db);
    }

    @Transactional
    public void updateUserProfile(SysUser user) {
        SysUser db = selectUserById(user.getUserId());
        if (db == null) {
            throw new ServiceException("用户不存在");
        }
        db.setNickName(user.getNickName());
        db.setPhonenumber(user.getPhonenumber());
        db.setEmail(user.getEmail());
        db.setSex(user.getSex());
        db.setUpdateTime(LocalDateTime.now());
        userRepository.save(db);
    }

    @Transactional
    public void updateUserPwd(Long userId, String encodedPassword) {
        SysUser db = selectUserById(userId);
        if (db == null) {
            throw new ServiceException("用户不存在");
        }
        db.setPassword(encodedPassword);
        db.setUpdateTime(LocalDateTime.now());
        userRepository.save(db);
    }

    @Transactional
    public void insertUserAuth(Long userId, Long[] roleIds) {
        userRoleRepository.deleteByUserId(userId);
        insertUserRole(userId, roleIds);
    }

    public List<SysUser> selectAllocatedList(Long roleId) {
        return userRepository.selectAllocatedByRoleId(roleId);
    }

    public List<SysUser> selectUnallocatedList(Long roleId) {
        List<SysUser> allocated = userRepository.selectAllocatedByRoleId(roleId);
        List<Long> allocatedIds = allocated.stream().map(SysUser::getUserId).toList();
        return userRepository.findAll(buildSpec(new SysUser())).stream()
                .filter(u -> !allocatedIds.contains(u.getUserId()))
                .toList();
    }
}
