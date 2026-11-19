package com.ppdv.api.track;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 跟踪模块配置：影子分异步执行使用独立调度线程，避免占用 MQTT 回调线程。
 * 调度线程池仅在 ppdv.track.enabled=true 时创建（与 IngestService/cleaner 的条件注册对齐）；
 * 关闭开关时不常驻守护线程。TrackProperties 始终启用：MqttConfig 需要读取开关本身。
 */
@Configuration
@EnableConfigurationProperties(TrackProperties.class)
public class TrackConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "ppdv.track", name = "enabled", havingValue = "true")
    public ScheduledExecutorService trackShadowScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "track-shadow");
            t.setDaemon(true);
            return t;
        });
    }
}
