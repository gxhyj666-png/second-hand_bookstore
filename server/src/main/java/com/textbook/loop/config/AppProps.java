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
    /** 订单支付超时（分钟）：待支付仅维持 5 分钟，超时自动关闭 */
    private int orderExpireMinutes = 5;
    /** 平台服务费比例 */
    private double commissionRate = 0.05;
    /** 运营推广提成比例（下线订单核销后按成交额计提） */
    private double promoterRate = 0.01;
    /** 上传文件落盘目录（图片/视频），生产可指向对象存储挂载点 */
    private String uploadDir = "./uploads";
    /** 核销码签名密钥 */
    private String verifySecret = "verify-code-dev-secret-please-change";
    /** 核销码轮换窗口（秒） */
    private int verifyWindowSeconds = 30;
    /** 核销码有效期（分钟）：买家出示、卖家核销 */
    private int verifyCodeTtlMinutes = 15;
    /** 短信验证码有效期（秒） */
    private int smsCodeTtlSeconds = 300;
    /** 开发模式：不真正发短信，验证码写日志并随响应返回；生产置 false 接阿里云 SMS */
    private boolean mockSms = true;

    /** 同一手机号两次发送验证码的最小间隔（秒）；开发期可调小以便反复跑冒烟脚本 */
    private int smsIntervalSeconds = 60;
    /** 短信通道：mock（默认，验证码回传）/ spug（Spug 推送助手短信验证码接口） */
    private String smsProvider = "mock";
    /** Spug 推送助手：官方短信验证码模板编码（{@code /sms/<TEMPLATE_CODE>} 里的那一段） */
    private String spugTemplateCode = "";
    /** Spug 推送助手接口基地址 */
    private String spugBaseUrl = "https://push.spug.cc";
    /** 调用 Spug 的超时（秒） */
    private int spugTimeoutSeconds = 8;
    /** 开发模式：实名/学生/人脸走本地模拟通过；生产置 false 接三方实人认证 */
    private boolean mockIdentity = true;
    /** ISBN 元数据通道：mock（只查本地教材库）/ market（阿里云云市场 ISBN 标准书号查询） */
    private String isbnProvider = "mock";
    /** 阿里云云市场 ISBN 查询接口地址 */
    private String aliyunMarketIsbnUrl = "https://lhisbnshcx.market.alicloudapi.com/isbn/query";
    /** 实名核验通道：mock（仅 GB11643 本地校验）/ aliyun（阿里云云市场身份证二要素） */
    private String identityProvider = "mock";
    /** 阿里云云市场二要素 AppCode（凭证，只放服务端，勿进前端/仓库） */
    private String aliyunMarketAppcode = "";
    /** 阿里云云市场二要素接口地址 */
    private String aliyunMarketIdcheckUrl = "https://verifytwo.market.alicloudapi.com/identity/verify_id_name";
    /** 实名核验调用超时（秒） */
    private int realnameTimeoutSeconds = 8;

    public String getIdentityProvider() {
        return identityProvider;
    }

    public void setIdentityProvider(String identityProvider) {
        this.identityProvider = identityProvider;
    }

    public String getAliyunMarketAppcode() {
        return aliyunMarketAppcode;
    }

    public void setAliyunMarketAppcode(String aliyunMarketAppcode) {
        this.aliyunMarketAppcode = aliyunMarketAppcode;
    }

    public String getAliyunMarketIdcheckUrl() {
        return aliyunMarketIdcheckUrl;
    }

    public void setAliyunMarketIdcheckUrl(String aliyunMarketIdcheckUrl) {
        this.aliyunMarketIdcheckUrl = aliyunMarketIdcheckUrl;
    }

    public int getRealnameTimeoutSeconds() {
        return realnameTimeoutSeconds;
    }

    public void setRealnameTimeoutSeconds(int realnameTimeoutSeconds) {
        this.realnameTimeoutSeconds = realnameTimeoutSeconds;
    }

    public String getSmsProvider() {
        return smsProvider;
    }

    public void setSmsProvider(String smsProvider) {
        this.smsProvider = smsProvider;
    }

    public String getSpugTemplateCode() {
        return spugTemplateCode;
    }

    public void setSpugTemplateCode(String spugTemplateCode) {
        this.spugTemplateCode = spugTemplateCode;
    }

    public String getSpugBaseUrl() {
        return spugBaseUrl;
    }

    public void setSpugBaseUrl(String spugBaseUrl) {
        this.spugBaseUrl = spugBaseUrl;
    }

    public int getSpugTimeoutSeconds() {
        return spugTimeoutSeconds;
    }

    public void setSpugTimeoutSeconds(int spugTimeoutSeconds) {
        this.spugTimeoutSeconds = spugTimeoutSeconds;
    }


    public int getVerifyCodeTtlMinutes() {
        return verifyCodeTtlMinutes;
    }

    public void setVerifyCodeTtlMinutes(int verifyCodeTtlMinutes) {
        this.verifyCodeTtlMinutes = verifyCodeTtlMinutes;
    }

    public int getSmsCodeTtlSeconds() {
        return smsCodeTtlSeconds;
    }

    public void setSmsCodeTtlSeconds(int smsCodeTtlSeconds) {
        this.smsCodeTtlSeconds = smsCodeTtlSeconds;
    }

    public boolean isMockSms() {
        return mockSms;
    }

    public void setMockSms(boolean mockSms) {
        this.mockSms = mockSms;
    }

    public int getSmsIntervalSeconds() {
        return smsIntervalSeconds;
    }

    public void setSmsIntervalSeconds(int smsIntervalSeconds) {
        this.smsIntervalSeconds = smsIntervalSeconds;
    }

    public boolean isMockIdentity() {
        return mockIdentity;
    }

    public void setMockIdentity(boolean mockIdentity) {
        this.mockIdentity = mockIdentity;
    }

    public String getIsbnProvider() {
        return isbnProvider;
    }

    public void setIsbnProvider(String isbnProvider) {
        this.isbnProvider = isbnProvider;
    }

    public String getAliyunMarketIsbnUrl() {
        return aliyunMarketIsbnUrl;
    }

    public void setAliyunMarketIsbnUrl(String aliyunMarketIsbnUrl) {
        this.aliyunMarketIsbnUrl = aliyunMarketIsbnUrl;
    }

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

    public double getPromoterRate() {
        return promoterRate;
    }

    public void setPromoterRate(double promoterRate) {
        this.promoterRate = promoterRate;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
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
