package com.ppdv.api.command;

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
}
