package com.ppdv.api.greenhouse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppdv.api.device.DeviceService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 最小 Spring 上下文冒烟测试（评审修复#1）：按组件扫描同等方式注册 RouteRecordingService，
 * 验证存在多个构造器时注入点可被 Spring 解析。生产上双构造器缺 @Autowired 会在
 * refresh 阶段抛 BeanInstantiationException，本测试即在该回归下失败。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RouteRecordingServiceContextTest.Config.class)
class RouteRecordingServiceContextTest {

    @Configuration
    @ComponentScan(basePackageClasses = RouteRecordingService.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = RouteRecordingService.class))
    static class Config {

        @Bean
        AreaService areaService() {
            return Mockito.mock(AreaService.class);
        }

        @Bean
        DeviceService deviceService() {
            return Mockito.mock(DeviceService.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    RouteRecordingService service;

    @Test
    void beanConstructsThroughAutowiredConstructor() {
        assertNotNull(service);
    }
}
