package com.textbook.loop.dto;

import com.textbook.loop.domain.SysUser;

/**
 * 登录/注册返回：token + 用户信息。
 */
public record TokenView(String token, SysUser user) {
}
