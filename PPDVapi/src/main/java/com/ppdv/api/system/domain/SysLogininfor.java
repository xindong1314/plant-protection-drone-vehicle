package com.ppdv.api.system.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "sys_logininfor")
@Getter
@Setter
public class SysLogininfor implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long infoId;

    private String userName;
    private String ipaddr;
    private String loginLocation;
    private String browser;
    private String os;
    private String status;
    private String msg;
    private LocalDateTime loginTime;

    /** 若依前端 addDateRange 透传的查询参数（params[beginTime] / params[endTime]），不映射数据库，也不序列化进 JSON。 */
    @JsonIgnore
    @Transient
    private Map<String, Object> params = new HashMap<>();
}
