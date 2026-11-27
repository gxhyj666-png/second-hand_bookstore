package com.textbook.loop.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textbook.loop.config.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 短信通道装配：按 {@code app.sms-provider} 选择 mock 或 Spug 推送助手。
 *
 * <p>配了 spug 但漏填模板编码时**回退 mock 并告警**，避免启动即挂、演示翻车。</p>
 */
@Configuration
public class SmsConfig {

    private static final Logger log = LoggerFactory.getLogger(SmsConfig.class);

    @Bean
    public SmsProvider smsProvider(AppProps props, ObjectMapper mapper) {
        String provider = props.getSmsProvider() == null ? "mock" : props.getSmsProvider().trim().toLowerCase();
        if ("spug".equals(provider)) {
            String template = props.getSpugTemplateCode();
            if (template == null || template.isBlank()) {
                log.warn("app.sms-provider=spug 但 app.spug-template-code 为空，短信通道回退 mock");
                return new MockSmsProvider();
            }
            log.info("短信通道：Spug 推送助手 {} （模板 {}）", props.getSpugBaseUrl(), template);
            return new SpugSmsProvider(props, mapper);
        }
        log.info("短信通道：mock（验证码随接口回传，仅开发使用）");
        return new MockSmsProvider();
    }
}
