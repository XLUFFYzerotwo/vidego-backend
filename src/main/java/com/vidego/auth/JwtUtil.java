package com.vidego.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtUtil(
            @Value("${vidego.jwt.secret}") String secret,
            @Value("${vidego.jwt.access-token-expiration}") long accessExp,
            @Value("${vidego.jwt.refresh-token-expiration}") long refreshExp) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessExp;
        this.refreshTokenExpiration = refreshExp;
    }

    /**
     * 生成 access token
     */
    public String generateAccessToken(Long userId) {
        return generateToken(userId, accessTokenExpiration);
    }

    /**
     * 生成 refresh token
     */
    public String generateRefreshToken(Long userId) {
        return generateToken(userId, refreshTokenExpiration);
    }

    private String generateToken(Long userId, long expiration) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从 token 中提取 userId
     */
    public Long getUserIdFromToken(String token) {
        try {
            String subject = parseClaims(token).getSubject();
            return Long.parseLong(subject);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证 token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 获取 token 过期时间
     */
    public Date getExpirationFromToken(String token) {
        return parseClaims(token).getExpiration();
    }

    /**
     * 获取 JWT 唯一 ID (jti)
     */
    public String getJtiFromToken(String token) {
        try {
            return parseClaims(token).getId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取 token 剩余有效时间 (毫秒)
     */
    public long getRemainingTtl(String token) {
        Date exp = getExpirationFromToken(token);
        return exp.getTime() - System.currentTimeMillis();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
