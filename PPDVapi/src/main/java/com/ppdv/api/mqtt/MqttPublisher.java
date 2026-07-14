package com.ppdv.api.mqtt;

import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 下行发布入口。仅在 ppdv.mqtt.enabled=true 时注册（见 MqttConfig）。
 */
public class MqttPublisher {

    private final MessageChannel outboundChannel;

    public MqttPublisher(MessageChannel outboundChannel) {
        this.outboundChannel = outboundChannel;
    }

    public void publish(String topic, String payload) {
        outboundChannel.send(MessageBuilder.withPayload(payload)
                .setHeader(MqttHeaders.TOPIC, topic)
                .build());
    }
}
