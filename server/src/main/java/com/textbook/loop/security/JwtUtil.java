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
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role == null ? "STUDENT" : role)
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
            return new LoginUser(Long.parseLong(claims.getSubject()), (String) claims.get("role"));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getTtlMillis() {
        return ttlMillis;
    }
}
