package com.ppdv.api.common.page;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

/**
 * 分页/排序查询参数，对齐若依前端约定的 pageNum / pageSize / orderByColumn / isAsc。
 */
public class PageQuery {

    private Integer pageNum;
    private Integer pageSize;
    private String orderByColumn;
    private String isAsc;

    /** 转为 Spring Data Pageable（pageNum 从 1 开始）。 */
    public Pageable toPageable() {
        int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        if (StringUtils.hasText(orderByColumn)) {
            Sort.Direction dir = "desc".equalsIgnoreCase(isAsc) ? Sort.Direction.DESC : Sort.Direction.ASC;
            return PageRequest.of(page - 1, size, Sort.by(dir, orderByColumn));
        }
        return PageRequest.of(page - 1, size);
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getOrderByColumn() {
        return orderByColumn;
    }

    public void setOrderByColumn(String orderByColumn) {
        this.orderByColumn = orderByColumn;
    }

    public String getIsAsc() {
        return isAsc;
    }

    public void setIsAsc(String isAsc) {
        this.isAsc = isAsc;
    }
}
