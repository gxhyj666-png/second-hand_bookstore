package com.textbook.loop.security;

import com.textbook.loop.common.BizException;

/**
 * 以 ThreadLocal 保存当前请求的登录用户，由拦截器写入、请求结束后清理。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 取当前用户，未登录抛 401。 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw BizException.unauthorized("请先登录");
        }
        return user;
    }

    public static Long requireUserId() {
        return require().userId();
    }

    /** 取当前用户，未登录返回 null（用于可选登录的接口，如商品列表）。 */
    public static Long currentUserIdOrNull() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && ("ADMIN".equals(user.role()) || "CLERK".equals(user.role()));
    }

    public static void requireAdmin() {
        if (!isAdmin()) {
            throw BizException.forbidden("需要管理员权限");
        }
    }
}
