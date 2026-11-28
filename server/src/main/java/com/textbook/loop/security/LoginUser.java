package com.textbook.loop.security;

/**
 * 当前登录用户（从 JWT 中解出，不含敏感字段）。
 *
 * <p>一个人只有一个账号，但可以叠加多本身份：{@code role} 是主身份（登录时用的那重），
 * {@code roles} 是"主身份 + 附加身份"的逗号串，例如商户进阶运营后为
 * {@code role=MERCHANT, roles=MERCHANT,OPERATOR}。</p>
 */
public record LoginUser(Long userId, String role, String roles) {

    /** 是否拥有某个身份（主身份或附加身份）。 */
    public boolean hasRole(String target) {
        if (target == null) {
            return false;
        }
        if (target.equalsIgnoreCase(role)) {
            return true;
        }
        if (roles == null || roles.isBlank()) {
            return false;
        }
        for (String r : roles.split(",")) {
            if (target.equalsIgnoreCase(r.trim())) {
                return true;
            }
        }
        return false;
    }
}
