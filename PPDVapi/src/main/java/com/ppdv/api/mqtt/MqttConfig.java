package com.ppdv.api.mqtt;

import com.ppdv.api.protocol.Topics;
import com.ppdv.api.telemetry.TelemetryIngestService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnProperty(prefix = "ppdv.mqtt", name = "enabled", havingValue = "true")
public class MqttConfig {

    private final MqttProperties props;

    public MqttConfig(MqttProperties props) {
        this.props = props;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{props.getUrl()});
        if (StringUtils.hasText(props.getUsername())) {
            options.setUserName(props.getUsername());
            options.setPassword(props.getPassword() == null ? new char[0] : props.getPassword().toCharArray());
        }
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(30);
        factory.setConnectionOptions(options);
        return factory;
    }

    // ---------- 上行：订阅遥测/状态/告警 ----------

    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInbound(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                props.getClientIdPrefix() + "-in", factory,
                Topics.SUB_STATUS, Topics.SUB_STATE, Topics.SUB_WORK,
                Topics.SUB_ALARM, Topics.SUB_PROGRESS, Topics.SUB_ACK);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public MessageHandler mqttInboundHandler(TelemetryIngestService ingest) {
        return message -> {
            String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
            ingest.onMessage(topic, message.getPayload().toString());
        };
    }

    // ---------- 下行：发布指令 ----------

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound(MqttPahoClientFactory factory) {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(
                props.getClientIdPrefix() + "-out", factory);
        handler.setAsync(true);
        handler.setDefaultQos(1);
        return handler;
    }

    @Bean
    public MqttPublisher mqttPublisher() {
        return new MqttPublisher(mqttOutboundChannel());
    }
}
