package com.textbook.loop.config;

import com.textbook.loop.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web 层配置：鉴权拦截器 + CORS。
 *
 * <p>验收审计发现原来用 {@code allowedOriginPatterns("*")} 配 {@code allowCredentials(true)}，
 * 等于允许任意站点带着 Cookie/JWT 跨域调用，属于典型的 CORS 配置事故。现在只放行
 * {@code app.cors-allowed-origins} 里显式列出的来源（默认只含本地开发端口）；
 * 生产环境前端与后端同源（Nginx 反代），根本不需要跨域，配空即可。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AppProps props;

    public WebConfig(AuthInterceptor authInterceptor, AppProps props) {
        this.authInterceptor = authInterceptor;
        this.props = props;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns("/v1/auth/login", "/v1/auth/register", "/v1/health");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String raw = props.getCorsAllowedOrigins();
        String[] origins = raw == null || raw.isBlank()
                ? new String[0]
                : Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (origins.length == 0) {
            // 同源部署（生产）不需要 CORS；显式不注册任何映射，绝不放开成 *
            return;
        }
        registry.addMapping("/v1/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 统一把 JSON 响应的 Content-Type 带上 charset=UTF-8。
     * 不带 charset 时，部分老客户端（如 Windows PowerShell 5.1 的 Invoke-RestMethod）会按 ISO-8859-1 解码，中文变乱码。
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
    }
}
