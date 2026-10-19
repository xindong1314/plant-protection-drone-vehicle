package com.ppdv.api.common.page;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

/**
 * 分页/排序查询参数，对齐若依前端约定的 pageNum / pageSize / orderByColumn / isAsc。
 */
public class PageQuery {

    /** 单页最大条数，pageSize 超过该值时截断，防止一次拉取全表。 */
    private static final int MAX_PAGE_SIZE = 500;

    private Integer pageNum;
    private Integer pageSize;
    private String orderByColumn;
    private String isAsc;

    /** 转为 Spring Data Pageable（pageNum 从 1 开始）。 */
    public Pageable toPageable() {
        int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        if (StringUtils.hasText(orderByColumn)) {
            // 兼容前端两种降序取值：'desc' 与 el-table sort-change 的 'descending'，其余（含 'asc'/'ascending'）按升序
            Sort.Direction dir = ("desc".equalsIgnoreCase(isAsc) || "descending".equalsIgnoreCase(isAsc)) ? Sort.Direction.DESC : Sort.Direction.ASC;
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
