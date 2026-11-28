package com.textbook.loop.sms;

import com.textbook.loop.common.Mask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 开发用模拟通道：不发真实短信，只把验证码写日志（配合接口回传，冒烟脚本与答辩演示无需真实号码）。
 */
public class MockSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean mock() {
        return true;
    }

    @Override
    public void sendCode(String phone, String code, int ttlSeconds) {
        // 手机号即便在开发日志里也脱敏，避免日志外传造成个人信息泄露
        log.info("【短信模拟】发送 {} 的验证码：{}（{} 秒内有效）", Mask.phone(phone), code, ttlSeconds);
    }
}
