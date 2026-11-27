package com.textbook.loop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 同窗书 · 校园教材循环平台 —— 后端启动类。
 */
@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class LoopApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoopApplication.class, args);
    }
}
