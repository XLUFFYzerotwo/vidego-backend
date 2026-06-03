package com.vidego.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 令牌管理 (黑名单模式)。
 * 登出时将 JWT 的 jti(唯一ID) 加入黑名单，使其在剩余有效期内失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String BLACKLIST_PREFIX = "vidego:blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    /**
     * 将 token 加入黑名单
     *
     * @param token 需要失效的 JWT
     */
    public void blacklist(String token) {
        String jti = jwtUtil.getJtiFromToken(token);
        if (jti == null) {
            log.warn("cannot extract jti from token, skipping blacklist");
            return;
        }
        long ttl = jwtUtil.getRemainingTtl(token);
        if (ttl <= 0) {
            log.debug("token already expired, no need to blacklist");
            return;
        }
        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "1", ttl, TimeUnit.MILLISECONDS);
        log.info("token blacklisted: jti={}, ttl={}ms", jti, ttl);
    }

    /**
     * 检查 token 是否已被拉黑
     *
     * @param token JWT
     * @return true 如果已黑名单
     */
    public boolean isBlacklisted(String token) {
        String jti = jwtUtil.getJtiFromToken(token);
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}
