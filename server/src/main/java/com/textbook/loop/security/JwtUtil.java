package com.textbook.loop.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.textbook.loop.config.AppProps;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析（HS256）。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long ttlMillis;

    public JwtUtil(AppProps props) {
        this.key = Keys.hmacShaKeyFor(pad(props.getJwtSecret()).getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = props.getJwtTtlHours() * 3600_000L;
    }

    private static String pad(String secret) {
        StringBuilder sb = new StringBuilder(secret == null ? "" : secret);
        while (sb.length() < 64) {
            sb.append("-textbook-loop");
        }
        return sb.toString();
    }

    public String issue(Long userId, String role) {
        return issue(userId, role, role);
    }

    /** 签发令牌：roles 是"主身份 + 附加身份"的逗号串（如 MERCHANT,OPERATOR）。 */
    public String issue(Long userId, String role, String roles) {
        long now = System.currentTimeMillis();
        String primary = role == null ? "STUDENT" : role;
        String all = roles == null || roles.isBlank() ? primary : roles;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", primary)
                .claim("roles", all)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，失败返回 null（由拦截器决定放行还是 401）。
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            String role = (String) claims.get("role");
            String roles = (String) claims.get("roles");
            return new LoginUser(Long.parseLong(claims.getSubject()), role,
                    roles == null || roles.isBlank() ? role : roles);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getTtlMillis() {
        return ttlMillis;
    }
}
