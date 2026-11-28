package com.textbook.loop.common;

/**
 * 日志脱敏工具。
 *
 * <p>验收审计发现 {@code SmsService} 把完整手机号写进日志，日志一旦外传就是个人信息泄露。
 * 统一走这里，保证任何日志里出现的手机号、身份证号都是掩码形式。</p>
 */
public final class Mask {

    private Mask() {
    }

    /** 13800000003 -> 138****0003 */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 110101199001011237 -> 1101**********1237 */
    public static String idNo(String idNo) {
        if (idNo == null || idNo.length() < 8) {
            return "***";
        }
        return idNo.substring(0, 4) + "**********" + idNo.substring(idNo.length() - 4);
    }

    /** 姓名只留姓：张三 -> 张* */
    public static String name(String name) {
        if (name == null || name.isEmpty()) {
            return "*";
        }
        return name.charAt(0) + "*".repeat(Math.max(1, name.length() - 1));
    }
}
