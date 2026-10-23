package com.ppdv.api.system.service;

import com.ppdv.api.common.exception.ServiceException;
import com.ppdv.api.system.domain.SysNotice;
import com.ppdv.api.system.domain.SysNoticeRead;
import com.ppdv.api.system.domain.vo.SysNoticeReadVo;
import com.ppdv.api.system.repository.SysNoticeReadRepository;
import com.ppdv.api.system.repository.SysNoticeRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知公告：管理端分页/增删改 + 头部铃铛列表与已读标记。
 */
@Service
public class SysNoticeService {

    /** 头部公告条数 */
    private static final int TOP_SIZE = 10;

    private final SysNoticeRepository noticeRepository;
    private final SysNoticeReadRepository noticeReadRepository;

    public SysNoticeService(SysNoticeRepository noticeRepository, SysNoticeReadRepository noticeReadRepository) {
        this.noticeRepository = noticeRepository;
        this.noticeReadRepository = noticeReadRepository;
    }

    /** 分页查询：公告标题/操作人模糊，类型/状态精确。 */
    public Page<SysNotice> selectPage(SysNotice filter, Pageable pageable) {
        return noticeRepository.findAll(buildSpec(filter), pageable);
    }

    private Specification<SysNotice> buildSpec(SysNotice filter) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filter != null) {
                if (StringUtils.hasText(filter.getNoticeTitle())) {
                    ps.add(cb.like(root.get("noticeTitle"), "%" + filter.getNoticeTitle() + "%"));
                }
                if (StringUtils.hasText(filter.getCreateBy())) {
                    ps.add(cb.like(root.get("createBy"), "%" + filter.getCreateBy() + "%"));
                }
                if (StringUtils.hasText(filter.getNoticeType())) {
                    ps.add(cb.equal(root.get("noticeType"), filter.getNoticeType()));
                }
                if (StringUtils.hasText(filter.getStatus())) {
                    ps.add(cb.equal(root.get("status"), filter.getStatus()));
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /**
     * 头部公告列表：最近 10 条已发布公告（status='0'，createTime 倒序），
     * 每条附当前用户已读标记 isRead，并统计前 10 条中的未读数 unreadCount。
     */
    public Map<String, Object> listTop(Long userId) {
        List<SysNotice> notices = noticeRepository.findByStatusOrderByCreateTimeDescNoticeIdDesc("0", PageRequest.of(0, TOP_SIZE));
        Set<Long> readNoticeIds = notices.isEmpty() ? Set.of()
                : noticeReadRepository.findByUserIdAndNoticeIdIn(userId, extractNoticeIds(notices)).stream()
                        .map(SysNoticeRead::getNoticeId)
                        .collect(Collectors.toSet());
        List<Map<String, Object>> rows = new ArrayList<>();
        int unreadCount = 0;
        for (SysNotice notice : notices) {
            boolean isRead = readNoticeIds.contains(notice.getNoticeId());
            if (!isRead) {
                unreadCount++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("noticeId", notice.getNoticeId());
            row.put("noticeTitle", notice.getNoticeTitle());
            row.put("noticeType", notice.getNoticeType());
            row.put("createTime", notice.getCreateTime());
            row.put("isRead", isRead);
            rows.add(row);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("unreadCount", unreadCount);
        return result;
    }

    public SysNotice selectById(Long noticeId) {
        return noticeRepository.findById(noticeId).orElse(null);
    }

    /** 标记已读（幂等：公告不存在直接跳过，并发唯一键冲突吞掉视为已读）。 */
    public void markRead(Long userId, Long noticeId) {
        if (!noticeRepository.existsById(noticeId)) {
            return;
        }
        if (noticeReadRepository.existsByNoticeIdAndUserId(noticeId, userId)) {
            return;
        }
        SysNoticeRead read = new SysNoticeRead();
        read.setNoticeId(noticeId);
        read.setUserId(userId);
        read.setReadTime(LocalDateTime.now());
        try {
            noticeReadRepository.save(read);
        } catch (DataIntegrityViolationException e) {
            // 并发下撞 uk_notice_user 唯一键，视为已读
        }
    }

    /** 批量标记已读（幂等：公告不存在跳过该 id，并发唯一键冲突吞掉视为已读）。 */
    public void markReadAll(Long userId, List<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return;
        }
        Set<Long> alreadyRead = noticeReadRepository.findByUserIdAndNoticeIdIn(userId, noticeIds).stream()
                .map(SysNoticeRead::getNoticeId)
                .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<SysNoticeRead> inserts = new ArrayList<>();
        for (Long noticeId : noticeIds) {
            if (alreadyRead.contains(noticeId) || !noticeRepository.existsById(noticeId)) {
                continue;
            }
            SysNoticeRead read = new SysNoticeRead();
            read.setNoticeId(noticeId);
            read.setUserId(userId);
            read.setReadTime(now);
            inserts.add(read);
        }
        if (!inserts.isEmpty()) {
            try {
                noticeReadRepository.saveAll(inserts);
            } catch (DataIntegrityViolationException e) {
                // 并发下撞 uk_notice_user 唯一键，视为已读
            }
        }
    }

    /** 已读用户分页（sys_user/sys_dept 投影），searchValue 模糊匹配登录名称/用户名称。 */
    public Page<SysNoticeReadVo> readUsers(Long noticeId, String searchValue, Pageable pageable) {
        String keyword = StringUtils.hasText(searchValue) ? searchValue : "";
        return noticeReadRepository.selectReadUsers(noticeId, keyword, pageable);
    }

    /** 新增：强制置空主键，防止请求体携带已有 noticeId 绕过 edit 检查改写公告。 */
    public SysNotice insert(SysNotice notice) {
        notice.setNoticeId(null);
        notice.setCreateTime(LocalDateTime.now());
        return noticeRepository.save(notice);
    }

    /** 修改：不存在直接报错；createBy/createTime 以库中为准，防止请求体伪造审计字段。 */
    public SysNotice update(SysNotice notice) {
        SysNotice dbNotice = noticeRepository.findById(notice.getNoticeId())
                .orElseThrow(() -> new ServiceException("公告不存在"));
        notice.setCreateBy(dbNotice.getCreateBy());
        notice.setCreateTime(dbNotice.getCreateTime());
        notice.setUpdateTime(LocalDateTime.now());
        return noticeRepository.save(notice);
    }

    /** 删除公告，同时清理其已读记录。 */
    @Transactional
    public void deleteByIds(Long[] noticeIds) {
        for (Long noticeId : noticeIds) {
            noticeRepository.deleteById(noticeId);
        }
        noticeReadRepository.deleteByNoticeIds(Arrays.asList(noticeIds));
    }

    private List<Long> extractNoticeIds(List<SysNotice> notices) {
        return notices.stream().map(SysNotice::getNoticeId).collect(Collectors.toList());
    }
}
