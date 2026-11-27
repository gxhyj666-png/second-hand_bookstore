package com.textbook.loop.util;

import com.textbook.loop.config.AppProps;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 核销码生成：HMAC-SHA256(secret, orderNo|window) 取前 6 位数字。
 * 按 window 轮换（默认 30 秒），核销时接受 当前/上一个 窗口，防止截图重放太久。
 */
@Component
public class VerifyCodeUtil {

    private final AppProps props;

    public VerifyCodeUtil(AppProps props) {
        this.props = props;
    }

    public String code(String orderNo) {
        return codeAt(orderNo, window(System.currentTimeMillis()));
    }

    public String codeAt(String orderNo, long window) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.getVerifySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal((orderNo + "|" + window).getBytes(StandardCharsets.UTF_8));
            int num = 0;
            for (int i = 0; i < 4; i++) {
                num = (num << 8) | (raw[i] & 0xFF);
            }
            return String.format("%06d", Math.abs(num % 1_000_000));
        } catch (Exception e) {
            throw new IllegalStateException("核销码生成失败", e);
        }
    }

    public long window(long epochMillis) {
        return epochMillis / 1000 / props.getVerifyWindowSeconds();
    }

    /**
     * 校验核销码：接受当前窗口与上一个窗口，避免刚好跨窗口时核销失败。
     */
    public boolean matches(String orderNo, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        long w = window(System.currentTimeMillis());
        return code.equals(codeAt(orderNo, w)) || code.equals(codeAt(orderNo, w - 1));
    }

    /** 核销码剩余有效秒数，供前端倒计时。 */
    public int remainSeconds() {
        long sec = System.currentTimeMillis() / 1000;
        int window = props.getVerifyWindowSeconds();
        return (int) (window - (sec % window));
    }
}
