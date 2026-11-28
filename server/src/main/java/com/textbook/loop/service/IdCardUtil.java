package com.textbook.loop.service;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * 身份证号本地工具：GB 11643 校验位 + 出生日期合法性。
 *
 * <p>只用于「格式是否可能是真身份证」的本地初筛（mock 通道、降级方案、前端提示），
 * 不构成真实性证明。</p>
 */
public final class IdCardUtil {

    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private IdCardUtil() {
    }

    public static boolean isValidIdNo(String idNo) {
        if (idNo == null || !idNo.matches("^\\d{17}[\\dXx]$")) {
            return false;
        }
        if (!validBirthday(idNo.substring(6, 14))) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idNo.charAt(i) - '0') * WEIGHTS[i];
        }
        char expected = CHECK_CODES[sum % 11];
        return Character.toUpperCase(idNo.charAt(17)) == expected;
    }

    private static boolean validBirthday(String yyyymmdd) {
        try {
            LocalDate d = LocalDate.of(Integer.parseInt(yyyymmdd.substring(0, 4)),
                    Integer.parseInt(yyyymmdd.substring(4, 6)),
                    Integer.parseInt(yyyymmdd.substring(6, 8)));
            return !d.isAfter(LocalDate.now().minusYears(10));
        } catch (DateTimeException | NumberFormatException e) {
            return false;
        }
    }
}
