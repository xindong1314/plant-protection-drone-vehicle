package com.ppdv.api.telemetry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * TDengine 装配：仅在 ppdv.tdengine.enabled=true 时生效。
 * 注意：只暴露 JdbcTemplate（DataSource 作为局部对象），避免影响 MySQL 主数据源的自动装配。
 */
@Configuration
@EnableConfigurationProperties(TdengineProperties.class)
@ConditionalOnProperty(prefix = "ppdv.tdengine", name = "enabled", havingValue = "true")
public class TdengineConfig {

    @Bean("tdengineJdbcTemplate")
    public JdbcTemplate tdengineJdbcTemplate(TdengineProperties props) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.taosdata.jdbc.rs.RestfulDriver");
        ds.setUrl(props.getUrl());
        ds.setUsername(props.getUsername());
        ds.setPassword(props.getPassword());
        return new JdbcTemplate(ds);
    }

    @Bean
    public TelemetryTsService telemetryTsService(JdbcTemplate tdengineJdbcTemplate, TdengineProperties props) {
        return new TelemetryTsService(tdengineJdbcTemplate, props.getDatabase());
    }
}
