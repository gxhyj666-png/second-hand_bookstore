package com.textbook.loop.realname;

import com.textbook.loop.common.Mask;
import com.textbook.loop.service.IdCardUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 开发用实名通道：不调用任何第三方，只做 GB 11643 校验位与出生日期合法性检查。
 *
 * <p>用于离线演示与自动化测试；它只能证明「号码格式合法」，不能证明「号码与姓名一致」，
 * 所以真实场景必须切到 {@link AliyunMarketRealNameProvider}。</p>
 */
public class MockRealNameProvider implements RealNameProvider {

    private static final Logger log = LoggerFactory.getLogger(MockRealNameProvider.class);

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean mock() {
        return true;
    }

    @Override
    public CheckResult check(String realName, String idNo) {
        boolean valid = IdCardUtil.isValidIdNo(idNo);
        log.info("【实名模拟】{} / {}，校验位{}", Mask.name(realName), mask(idNo), valid ? "通过" : "不通过");
        return new CheckResult(valid,
                valid ? "MOCK 通过：身份证号校验位与出生日期合法（未调用三方）" : "MOCK 不通过：身份证号校验位错误");
    }

    private String mask(String idNo) {
        if (idNo == null || idNo.length() < 10) {
            return "***";
        }
        return idNo.substring(0, 4) + "****" + idNo.substring(idNo.length() - 4);
    }
}
