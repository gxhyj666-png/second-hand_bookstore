package com.textbook.loop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用自定义配置（application.yml 的 app.* 前缀）。
 */
@ConfigurationProperties(prefix = "app")
public class AppProps {

    /** JWT 签名密钥，长度至少 32 字节 */
    private String jwtSecret = "textbook-loop-dev-secret-key-please-change-in-production";
    /** JWT 有效期（小时） */
    private int jwtTtlHours = 168;
    /** 订单支付超时（分钟） */
    private int orderExpireMinutes = 15;
    /** 平台服务费比例 */
    private double commissionRate = 0.05;
    /** 核销码签名密钥 */
    private String verifySecret = "verify-code-dev-secret-please-change";
    /** 核销码轮换窗口（秒） */
    private int verifyWindowSeconds = 30;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public int getJwtTtlHours() {
        return jwtTtlHours;
    }

    public void setJwtTtlHours(int jwtTtlHours) {
        this.jwtTtlHours = jwtTtlHours;
    }

    public int getOrderExpireMinutes() {
        return orderExpireMinutes;
    }

    public void setOrderExpireMinutes(int orderExpireMinutes) {
        this.orderExpireMinutes = orderExpireMinutes;
    }

    public double getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(double commissionRate) {
        this.commissionRate = commissionRate;
    }

    public String getVerifySecret() {
        return verifySecret;
    }

    public void setVerifySecret(String verifySecret) {
        this.verifySecret = verifySecret;
    }

    public int getVerifyWindowSeconds() {
        return verifyWindowSeconds;
    }

    public void setVerifyWindowSeconds(int verifyWindowSeconds) {
        this.verifyWindowSeconds = verifyWindowSeconds;
    }
}
