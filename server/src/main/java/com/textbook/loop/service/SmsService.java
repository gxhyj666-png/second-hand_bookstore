package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.textbook.loop.common.BizException;
import com.textbook.loop.config.AppProps;
import com.textbook.loop.domain.SysSmsCode;
import com.textbook.loop.mapper.SysSmsCodeMapper;
import com.textbook.loop.sms.SmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 短信验证码：登录 / 交易二次验证。
 *
 * <p>真实发送交给 {@link SmsProvider}：开发默认 mock（验证码写日志并随接口回传，方便自动化与演示），
 * 生产把 {@code app.sms-provider} 置为 {@code spug} 并填 {@code app.spug-template-code}，
 * 即走 Spug 推送助手的官方短信验证码模板。</p>
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SysSmsCodeMapper codeMapper;
    private final AppProps props;
    private final SmsProvider provider;

    public SmsService(SysSmsCodeMapper codeMapper, AppProps props, SmsProvider provider) {
        this.codeMapper = codeMapper;
        this.props = props;
        this.provider = provider;
    }

    /**
     * 发送验证码，返回开发模式下的明文验证码（生产返回 null）。
     */
    @Transactional
    public String send(String phone, String purpose) {
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw BizException.badRequest("手机号格式不正确");
        }
        LocalDateTime now = LocalDateTime.now();
        SysSmsCode last = codeMapper.selectOne(new LambdaQueryWrapper<SysSmsCode>()
                .eq(SysSmsCode::getPhone, phone)
                .eq(SysSmsCode::getPurpose, purpose)
                .orderByDesc(SysSmsCode::getId)
                .last("limit 1"));
        int interval = Math.max(0, props.getSmsIntervalSeconds());
        if (interval > 0 && last != null && last.getCreatedAt() != null
                && last.getCreatedAt().isAfter(now.minusSeconds(interval))) {
            throw BizException.conflict("发送过于频繁，请 " + interval + " 秒后再试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        SysSmsCode row = new SysSmsCode();
        row.setPhone(phone);
        row.setCode(code);
        row.setPurpose(purpose);
        row.setExpireAt(now.plusSeconds(props.getSmsCodeTtlSeconds()));
        row.setUsed(0);
        codeMapper.insert(row);
        int ttl = props.getSmsCodeTtlSeconds();
        provider.sendCode(phone, code, ttl);
        log.info("验证码已创建：phone={} purpose={} provider={} ttl={}s", phone, purpose, provider.name(), ttl);
        return provider.mock() ? code : null;
    }

    /**
     * 当前生效的短信通道名（mock / spug），供接口自检与前端提示。
     */
    public String providerName() {
        return provider.name();
    }

    /**
     * 校验验证码；校验通过即置为已使用，避免重复使用。
     */
    @Transactional
    public void verify(String phone, String purpose, String code) {
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("验证码不能为空");
        }
        SysSmsCode row = codeMapper.selectOne(new LambdaQueryWrapper<SysSmsCode>()
                .eq(SysSmsCode::getPhone, phone)
                .eq(SysSmsCode::getPurpose, purpose)
                .eq(SysSmsCode::getUsed, 0)
                .orderByDesc(SysSmsCode::getId)
                .last("limit 1"));
        if (row == null || !row.getCode().equals(code.trim())) {
            throw BizException.unauthorized("验证码不正确");
        }
        if (row.getExpireAt() != null && row.getExpireAt().isBefore(LocalDateTime.now())) {
            throw BizException.unauthorized("验证码已过期，请重新获取");
        }
        row.setUsed(1);
        codeMapper.updateById(row);
    }
}
