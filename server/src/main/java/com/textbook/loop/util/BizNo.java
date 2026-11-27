package com.textbook.loop.util;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 业务编号生成：订单号、批次号、邀请码。
 */
public final class BizNo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private BizNo() {
    }

    /** 订单号：TB + yyyyMMddHHmmss + 6 位随机，长度 22。 */
    public static String orderNo() {
        return "TB" + LocalDateTime.now().format(TS) + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** 结算批次号。 */
    public static String batchNo() {
        return "ST" + LocalDateTime.now().format(TS);
    }

    /** 接龙邀请码：6 位无歧义大写字母数字。 */
    public static String inviteCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
