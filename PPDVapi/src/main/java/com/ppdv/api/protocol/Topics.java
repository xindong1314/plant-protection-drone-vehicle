package com.ppdv.api.protocol;

/**
 * MQTT 主题约定，对应 docs/protocol.md（v0.1）。
 * 前缀 ppdv/v1/{deviceId}/...
 */
public final class Topics {

    public static final String PREFIX = "ppdv/v1/";

    private Topics() {
    }

    // 上行订阅（含单层通配符 + 匹配 deviceId）
    public static final String SUB_STATUS = PREFIX + "+/status";
    public static final String SUB_STATE = PREFIX + "+/tele/state";
    public static final String SUB_WORK = PREFIX + "+/tele/work";
    public static final String SUB_ALARM = PREFIX + "+/event/alarm";
    public static final String SUB_PROGRESS = PREFIX + "+/task/progress";
    public static final String SUB_ENV = PREFIX + "+/tele/env";
    public static final String SUB_ZONE = PREFIX + "+/tele/zone";
    public static final String SUB_ACK = PREFIX + "+/cmd/ack";

    // 下行发布
    public static String cmdReq(String deviceId) {
        return PREFIX + deviceId + "/cmd/req";
    }

    public static String taskAssign(String deviceId) {
        return PREFIX + deviceId + "/task/assign";
    }

    public static String configSet(String deviceId) {
        return PREFIX + deviceId + "/config/set";
    }

    public static String sprayReq(String deviceId) {
        return PREFIX + deviceId + "/spray/req";
    }

    /** 从上行主题解析出 deviceId：ppdv/v1/{deviceId}/... */
    public static String deviceIdOf(String topic) {
        String[] p = topic.split("/");
        return p.length >= 3 ? p[2] : null;
    }

    /** 主题的业务种类，如 status / tele/state / event/alarm */
    public static String kindOf(String topic) {
        String[] p = topic.split("/");
        if (p.length < 4) {
            return "";
        }
        StringBuilder sb = new StringBuilder(p[3]);
        for (int i = 4; i < p.length; i++) {
            sb.append('/').append(p[i]);
        }
        return sb.toString();
    }
}
