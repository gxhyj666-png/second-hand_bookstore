package com.textbook.loop.security;

/**
 * 当前登录用户（从 JWT 中解出，不含敏感字段）。
 */
public record LoginUser(Long userId, String role) {
}
