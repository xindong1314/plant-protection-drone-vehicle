package com.ppdv.api.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * 统一消息信封，对应 docs/protocol.md 第 4 节。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Envelope {

    private String msgId;
    private String deviceId;
    private long ts;
    private String type;
    private JsonNode data;

    public Envelope() {
    }

    public static Envelope of(String deviceId, String type, JsonNode data) {
        Envelope e = new Envelope();
        e.msgId = UUID.randomUUID().toString();
        e.deviceId = deviceId;
        e.ts = System.currentTimeMillis();
        e.type = type;
        e.data = data;
        return e;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }
}
