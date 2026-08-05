package com.ppdv.api.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 遥测时序读写（TDengine）。仅在 ppdv.tdengine.enabled=true 时由 TdengineConfig 装配。
 * 超级表 tele_state / tele_work，按 deviceId 自动建子表。
 */
public class TelemetryTsService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(TelemetryTsService.class);

    private final JdbcTemplate jdbc;
    private final String db;

    public TelemetryTsService(JdbcTemplate jdbc, String database) {
        this.jdbc = jdbc;
        this.db = database;
    }

    @Override
    public void afterPropertiesSet() {
        jdbc.execute("CREATE DATABASE IF NOT EXISTS " + db + " PRECISION 'ms' KEEP 3650");
        jdbc.execute("CREATE STABLE IF NOT EXISTS " + db + ".tele_state (" +
                "ts TIMESTAMP, lat DOUBLE, lon DOUBLE, alt DOUBLE, heading DOUBLE, speed DOUBLE, " +
                "battery INT, mode NCHAR(16)) TAGS (device_id NCHAR(64))");
        jdbc.execute("CREATE STABLE IF NOT EXISTS " + db + ".tele_work (" +
                "ts TIMESTAMP, spraying BOOL, flow DOUBLE, pressure DOUBLE, tank_level INT, " +
                "sprayed_area DOUBLE, dosage DOUBLE) TAGS (device_id NCHAR(64))");
        jdbc.execute("CREATE STABLE IF NOT EXISTS " + db + ".env_sensor (" +
                "ts TIMESTAMP, temperature DOUBLE, humidity DOUBLE, light DOUBLE, " +
                "soil_moisture DOUBLE, co2 DOUBLE) TAGS (device_id NCHAR(64))");
        log.info("TDengine 时序库已就绪: {}", db);
    }

    /** 写状态遥测（位置/速度/电量/模式）。 */
    public void writeState(String deviceId, long ts, JsonNode data) {
        String sub = subTable(deviceId);
        String sql = "INSERT INTO " + db + "." + sub + " USING " + db + ".tele_state TAGS ('" + esc(deviceId) + "') " +
                "VALUES (" + ts + ", " +
                num(data, "lat") + ", " + num(data, "lon") + ", " + num(data, "alt") + ", " +
                num(data, "heading") + ", " + num(data, "speed") + ", " +
                intOrNull(data, "battery") + ", " + strOrNull(data, "mode") + ")";
        safeExec(sql);
    }

    /** 写作业遥测（喷洒/流量/药量）。 */
    public void writeWork(String deviceId, long ts, JsonNode data) {
        String sub = "w_" + subTable(deviceId).substring(2);
        String sql = "INSERT INTO " + db + "." + sub + " USING " + db + ".tele_work TAGS ('" + esc(deviceId) + "') " +
                "VALUES (" + ts + ", " +
                boolOrNull(data, "spraying") + ", " + num(data, "flow") + ", " + num(data, "pressure") + ", " +
                intOrNull(data, "tankLevel") + ", " + num(data, "sprayedArea") + ", " + num(data, "dosage") + ")";
        safeExec(sql);
    }

    /** 查询某设备的状态遥测历史（按时间倒序）。 */
    public List<Map<String, Object>> queryStateHistory(String deviceId, int limit) {
        String sql = "SELECT ts, lat, lon, alt, heading, speed, battery, mode FROM " + db + ".tele_state " +
                "WHERE device_id = ? ORDER BY ts DESC LIMIT " + Math.max(1, Math.min(limit, 5000));
        try {
            return jdbc.queryForList(sql, deviceId);
        } catch (Exception e) {
            log.warn("查询遥测历史失败 device={}, err={}", deviceId, e.getMessage());
            return List.of();
        }
    }

    /** 写环境传感器遥测（温度/湿度/光照/土壤湿度/CO2）。 */
    public void writeEnv(String deviceId, long ts, JsonNode data) {
        String sub = "e_" + subTable(deviceId).substring(2);
        String sql = "INSERT INTO " + db + "." + sub + " USING " + db + ".env_sensor TAGS ('" + esc(deviceId) + "') " +
                "VALUES (" + ts + ", " +
                num(data, "temperature") + ", " + num(data, "humidity") + ", " + num(data, "light") + ", " +
                num(data, "soilMoisture") + ", " + num(data, "co2") + ")";
        safeExec(sql);
    }

    /** 查询某设备的环境遥测历史（按时间倒序）。 */
    public List<Map<String, Object>> queryEnvHistory(String deviceId, int limit) {
        String sql = "SELECT ts, temperature, humidity, light, soil_moisture, co2 FROM " + db + ".env_sensor " +
                "WHERE device_id = ? ORDER BY ts DESC LIMIT " + Math.max(1, Math.min(limit, 5000));
        try {
            return jdbc.queryForList(sql, deviceId);
        } catch (Exception e) {
            log.warn("查询环境历史失败 device={}, err={}", deviceId, e.getMessage());
            return List.of();
        }
    }

    private void safeExec(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception e) {
            log.warn("写入 TDengine 失败: {} | err={}", sql, e.getMessage());
        }
    }

    private String subTable(String deviceId) {
        return "d_" + deviceId.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("'", "");
    }

    private String num(JsonNode data, String field) {
        return data != null && data.hasNonNull(field) ? String.valueOf(data.get(field).asDouble()) : "NULL";
    }

    private String intOrNull(JsonNode data, String field) {
        return data != null && data.hasNonNull(field) ? String.valueOf(data.get(field).asInt()) : "NULL";
    }

    private String boolOrNull(JsonNode data, String field) {
        return data != null && data.hasNonNull(field) ? (data.get(field).asBoolean() ? "true" : "false") : "NULL";
    }

    private String strOrNull(JsonNode data, String field) {
        return data != null && data.hasNonNull(field) ? "'" + esc(data.get(field).asText()) + "'" : "NULL";
    }
}
