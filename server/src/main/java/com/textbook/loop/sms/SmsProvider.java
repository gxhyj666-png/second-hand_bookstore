package com.textbook.loop.sms;

/**
 * 短信通道抽象。业务只依赖这个接口，切换供应商（mock / Spug 推送助手 / 阿里云 SMS）不改业务代码。
 */
public interface SmsProvider {

    /** 通道名，用于日志与 /v1/health 自检 */
    String name();

    /** 是否开发模拟通道：为 true 时验证码可随接口回传，方便自动化 */
    boolean mock();

    /**
     * 发送验证码。
     *
     * @param phone      手机号（11 位）
     * @param code       验证码（4-6 位）
     * @param ttlSeconds 有效期（秒）
     */
    void sendCode(String phone, String code, int ttlSeconds);
}
