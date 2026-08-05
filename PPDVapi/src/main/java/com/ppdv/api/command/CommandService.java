package com.ppdv.api.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ppdv.api.device.DeviceService;
import com.ppdv.api.mqtt.MqttPublisher;
import com.ppdv.api.protocol.Envelope;
import com.ppdv.api.protocol.Topics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CommandService {

    private final ObjectProvider<MqttPublisher> publisherProvider;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    public CommandService(ObjectProvider<MqttPublisher> publisherProvider,
                          DeviceService deviceService,
                          ObjectMapper objectMapper) {
        this.publisherProvider = publisherProvider;
        this.deviceService = deviceService;
        this.objectMapper = objectMapper;
    }

    /** 下发控制指令，返回本次 msgId（车载端回执时以此为 refId）。 */
    public String sendCommand(String deviceId, String cmd, Map<String, Object> args) {
        deviceService.get(deviceId); // 校验设备存在
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("MQTT 未启用 (ppdv.mqtt.enabled=false)，无法下发指令");
        }

        ObjectNode data = objectMapper.createObjectNode();
        data.put("cmd", cmd);
        if (args != null && !args.isEmpty()) {
            data.set("args", objectMapper.valueToTree(args));
        }
        Envelope envelope = Envelope.of(deviceId, "cmd", data);
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            publisher.publish(Topics.cmdReq(deviceId), payload);
            return envelope.getMsgId();
        } catch (Exception e) {
            throw new IllegalStateException("指令序列化失败: " + e.getMessage(), e);
        }
    }

    /** 下发点喷指令（start/stop），返回本次 msgId。发布到 spray/req。 */
    public String sendSpray(String deviceId, String action, String areaCode, Integer duration) {
        deviceService.get(deviceId);
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("MQTT 未启用 (ppdv.mqtt.enabled=false)，无法下发点喷指令");
        }
        ObjectNode data = objectMapper.createObjectNode();
        data.put("action", action);
        data.put("areaCode", areaCode);
        if (duration != null) {
            data.put("duration", duration);
        }
        Envelope envelope = Envelope.of(deviceId, "spray", data);
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            publisher.publish(Topics.sprayReq(deviceId), payload);
            return envelope.getMsgId();
        } catch (Exception e) {
            throw new IllegalStateException("点喷指令序列化失败: " + e.getMessage(), e);
        }
    }

    /** 下发作业任务（航线/地块/参数），返回本次 msgId。发布到 task/assign。 */
    public String sendTask(String deviceId, Map<String, Object> taskData) {
        deviceService.get(deviceId);
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("MQTT 未启用 (ppdv.mqtt.enabled=false)，无法下发任务");
        }
        JsonNode data = objectMapper.valueToTree(taskData == null ? Map.of() : taskData);
        Envelope envelope = Envelope.of(deviceId, "task", data);
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            publisher.publish(Topics.taskAssign(deviceId), payload);
            return envelope.getMsgId();
        } catch (Exception e) {
            throw new IllegalStateException("任务序列化失败: " + e.getMessage(), e);
        }
    }
}
