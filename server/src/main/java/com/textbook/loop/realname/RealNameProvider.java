package com.textbook.loop.realname;

/**
 * 身份证实名核验通道抽象（二要素：姓名 + 身份证号）。
 *
 * <p>与 {@code sms/} 一样是 provider 结构：mock 走本地校验，aliyun 走阿里云云市场的
 * 「身份证二要素核验」API（AppCode 鉴权）。业务代码只依赖本接口。</p>
 */
public interface RealNameProvider {

    String name();

    boolean mock();

    /**
     * 校验「姓名 + 身份证号」是否一致。
     *
     * @param realName 真实姓名
     * @param idNo     18 位身份证号
     * @return 是否一致、通道返回的说明
     */
    CheckResult check(String realName, String idNo);

    record CheckResult(boolean passed, String message) {
    }
}
