package com.textbook.loop.realname;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textbook.loop.config.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 实名核验通道装配：按 {@code app.identity-provider} 选择 mock 或阿里云云市场二要素。
 *
 * <p>与短信通道同样的兜底策略：配了 aliyun 但 AppCode 为空时**回退 mock 并告警**，
 * 避免启动即挂、演示翻车。</p>
 */
@Configuration
public class RealNameConfig {

    private static final Logger log = LoggerFactory.getLogger(RealNameConfig.class);

    @Bean
    public RealNameProvider realNameProvider(AppProps props, ObjectMapper mapper) {
        String provider = props.getIdentityProvider() == null ? "mock" : props.getIdentityProvider().trim().toLowerCase();
        if ("aliyun".equals(provider) || "aliyun-market".equals(provider)) {
            String appcode = props.getAliyunMarketAppcode();
            if (appcode == null || appcode.isBlank()) {
                log.warn("app.identity-provider=aliyun 但 app.aliyun-market-appcode 为空，实名通道回退 mock");
                return new MockRealNameProvider();
            }
            log.info("实名通道：阿里云云市场二要素 {}（AppCode {}****）",
                    props.getAliyunMarketIdcheckUrl(), appcode.substring(0, Math.min(4, appcode.length())));
            return new AliyunMarketRealNameProvider(props, mapper);
        }
        log.info("实名通道：mock（只做 GB11643 本地校验，仅开发使用）");
        return new MockRealNameProvider();
    }
}
