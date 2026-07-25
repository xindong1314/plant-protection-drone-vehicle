package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.system.domain.SysPost;
import com.ppdv.api.system.service.SysPostService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/post")
public class SysPostController extends BaseController {

    private final SysPostService postService;

    public SysPostController(SysPostService postService) {
        this.postService = postService;
    }

    @PreAuthorize("@ss.hasPermi('system:post:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysPost filter, PageQuery pageQuery) {
        return getDataTable(postService.selectPostPage(filter, pageQuery.toPageable()));
    }

    @PreAuthorize("@ss.hasPermi('system:post:query')")
    @GetMapping("/{postId}")
    public AjaxResult getInfo(@PathVariable Long postId) {
        return AjaxResult.success(postService.selectPostById(postId));
    }

    @GetMapping("/optionselect")
    public AjaxResult optionselect() {
        return AjaxResult.success(postService.selectPostAll());
    }

    @PreAuthorize("@ss.hasPermi('system:post:add')")
    @PostMapping
    public AjaxResult add(@RequestBody SysPost post) {
        if (!postService.checkPostNameUnique(post)) {
            return AjaxResult.error("新增岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        }
        if (!postService.checkPostCodeUnique(post)) {
            return AjaxResult.error("新增岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        }
        post.setCreateBy(username());
        postService.insertPost(post);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:post:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody SysPost post) {
        post.setUpdateBy(username());
        postService.updatePost(post);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:post:remove')")
    @DeleteMapping("/{postIds}")
    public AjaxResult remove(@PathVariable Long[] postIds) {
        postService.deletePostByIds(postIds);
        return AjaxResult.success();
    }
}
