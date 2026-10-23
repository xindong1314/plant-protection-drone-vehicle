package com.ppdv.api.system.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公共审计字段。JPA 自动填充 createTime / updateTime。
 */
@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
    }

    @PreUpdate
    protected void preUpdate() {
        updateTime = LocalDateTime.now();
    }

    @Column(name = "create_by")
    private String createBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_by")
    private String updateBy;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "remark")
    private String remark;

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    /** 若依前端 addDateRange 透传的查询参数（params[beginTime] / params[endTime]），不映射数据库。 */
    @jakarta.persistence.Transient
    private java.util.Map<String, Object> params = new java.util.HashMap<>();

    /** 仅承接前端 addDateRange 的查询参数绑定，不参与数据库映射，也不序列化进 JSON（避免改变既有接口输出）。 */
    @JsonIgnore
    public java.util.Map<String, Object> getParams() {
        return params;
    }

    public void setParams(java.util.Map<String, Object> params) {
        this.params = params;
    }
}
