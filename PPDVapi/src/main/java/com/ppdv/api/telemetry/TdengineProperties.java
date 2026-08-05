package com.ppdv.api.telemetry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TDengine 配置：ppdv.tdengine.*（REST 连接）。
 */
@ConfigurationProperties(prefix = "ppdv.tdengine")
public class TdengineProperties {

    private boolean enabled = false;
    /** REST/WebSocket 连接串，不含库名，如 jdbc:TAOS-RS://localhost:6041 */
    private String url = "jdbc:TAOS-RS://localhost:6041";
    private String username = "root";
    private String password = "taosdata";
    private String database = "ppdv_tsdb";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }
}
