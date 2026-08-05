package com.ppdv.api.system.service;

import com.ppdv.api.common.exception.ServiceException;
import com.ppdv.api.system.domain.SysPost;
import com.ppdv.api.system.repository.SysPostRepository;
import com.ppdv.api.system.repository.SysUserPostRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 岗位服务。
 */
@Service
public class SysPostService {

    private final SysPostRepository postRepository;
    private final SysUserPostRepository userPostRepository;

    public SysPostService(SysPostRepository postRepository, SysUserPostRepository userPostRepository) {
        this.postRepository = postRepository;
        this.userPostRepository = userPostRepository;
    }

    public Page<SysPost> selectPostPage(SysPost filter, Pageable pageable) {
        return postRepository.findAll(buildSpec(filter), pageable);
    }

    private Specification<SysPost> buildSpec(SysPost filter) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filter != null) {
                if (StringUtils.hasText(filter.getPostCode())) {
                    ps.add(cb.like(root.get("postCode"), "%" + filter.getPostCode() + "%"));
                }
                if (StringUtils.hasText(filter.getPostName())) {
                    ps.add(cb.like(root.get("postName"), "%" + filter.getPostName() + "%"));
                }
                if (StringUtils.hasText(filter.getStatus())) {
                    ps.add(cb.equal(root.get("status"), filter.getStatus()));
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    public List<SysPost> selectPostAll() {
        return postRepository.findByOrderByPostSortAsc();
    }

    public SysPost selectPostById(Long postId) {
        return postRepository.findById(postId).orElse(null);
    }

    public boolean checkPostNameUnique(SysPost post) {
        return !postRepository.existsByPostName(post.getPostName());
    }

    public boolean checkPostCodeUnique(SysPost post) {
        return !postRepository.existsByPostCode(post.getPostCode());
    }

    public SysPost insertPost(SysPost post) {
        post.setCreateTime(LocalDateTime.now());
        return postRepository.save(post);
    }

    public SysPost updatePost(SysPost post) {
        post.setUpdateTime(LocalDateTime.now());
        return postRepository.save(post);
    }

    public void deletePostByIds(Long[] postIds) {
        for (Long postId : postIds) {
            SysPost post = selectPostById(postId);
            if (post != null && userPostRepository.countByPostId(postId) > 0) {
                throw new ServiceException(post.getPostName() + "已分配，不能删除");
            }
        }
        for (Long postId : postIds) {
            postRepository.deleteById(postId);
        }
    }
}
