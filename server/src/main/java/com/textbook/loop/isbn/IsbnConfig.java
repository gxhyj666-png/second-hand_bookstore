package com.textbook.loop.isbn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textbook.loop.config.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ISBN 元数据通道装配：{@code app.isbn-provider=mock|market}。
 * 配了 market 但 AppCode 为空时回退 mock 并告警，避免启动即挂。
 */
@Configuration
public class IsbnConfig {

    private static final Logger log = LoggerFactory.getLogger(IsbnConfig.class);

    @Bean
    public IsbnMetaProvider isbnMetaProvider(AppProps props, ObjectMapper mapper) {
        String provider = props.getIsbnProvider() == null ? "mock" : props.getIsbnProvider().trim().toLowerCase();
        if ("market".equals(provider) || "aliyun".equals(provider) || "aliyun-market".equals(provider)) {
            String appcode = props.getAliyunMarketAppcode();
            if (appcode == null || appcode.isBlank()) {
                log.warn("app.isbn-provider=market 但 app.aliyun-market-appcode 为空，ISBN 元数据通道回退 mock");
                return new MockIsbnMetaProvider();
            }
            log.info("ISBN 元数据通道：阿里云云市场 {}（AppCode {}****）",
                    props.getAliyunMarketIsbnUrl(), appcode.substring(0, Math.min(4, appcode.length())));
            return new AliyunMarketIsbnProvider(props, mapper);
        }
        log.info("ISBN 元数据通道：mock（只查本地教材库，未命中引导手填）");
        return new MockIsbnMetaProvider();
    }
}
