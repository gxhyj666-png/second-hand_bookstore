package com.textbook.loop.dto;

import com.textbook.loop.domain.SysUser;

import java.util.List;

/**
 * 登录/注册返回：token + 用户信息 + 全部身份（主身份在前）。
 *
 * <p>{@code roles} 是"主身份 + 附加身份"，前端据此决定显示商户 / 运营入口，
 * 例如商户进阶运营后为 {@code ["MERCHANT", "OPERATOR"]}。</p>
 */
public record TokenView(String token, SysUser user, List<String> roles) {
}
