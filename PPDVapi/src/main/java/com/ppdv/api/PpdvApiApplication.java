package com.ppdv.api;

import com.ppdv.api.config.FileStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FileStorageProperties.class)
public class PpdvApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PpdvApiApplication.class, args);
    }
}
