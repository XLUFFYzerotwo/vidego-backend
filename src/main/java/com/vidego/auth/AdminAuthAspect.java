package com.vidego.auth;

import com.vidego.common.exception.BusinessException;
import com.vidego.common.result.ErrorCode;
import com.vidego.module.user.entity.User;
import com.vidego.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 管理员权限校验切面
 *
 * <p>拦截 {@link RequireAdmin} 注解的方法/类，校验流程：</p>
 * <pre>
 * 请求 → JwtAuthenticationFilter (解析 JWT, 设置 userId 到 UserContext)
 *      → AdminAuthAspect (检查 userId 对应的 role 是否为 1)
 *          ├─ 是管理员 → 放行执行业务逻辑
 *          └─ 不是管理员 → 抛出 403 FORBIDDEN
 * </pre>
 *
 * <p>角色信息缓存到 Redis，key = {@code vidego:user:role:{userId}}，TTL = 10 分钟，
 * 避免每个请求都查数据库。管理员角色变更时调用 {@link #invalidateRoleCache(Long)} 清除缓存。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(1)
public class AdminAuthAspect {

    /** Redis 缓存 key 前缀 */
    public static final String CACHE_ROLE_PREFIX = "vidego:user:role:";
    /** 缓存 TTL（秒） */
    public static final long CACHE_ROLE_TTL = 600;

    /** 角色值：管理员 */
    public static final int ROLE_ADMIN = 1;

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 切入点：所有标注了 @RequireAdmin 的方法或类
     */
    @Pointcut("@within(com.vidego.auth.RequireAdmin) || @annotation(com.vidego.auth.RequireAdmin)")
    public void requireAdminPointcut() {
    }

    @Before("requireAdminPointcut()")
    public void checkAdmin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!isAdmin(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "admin role required");
        }
    }

    /**
     * 判断用户是否为管理员（带 Redis 缓存）
     */
    public boolean isAdmin(Long userId) {
        String cacheKey = CACHE_ROLE_PREFIX + userId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return "1".equals(cached);
            }
        } catch (Exception e) {
            // Redis 异常不影响主流程，降级到数据库
            log.warn("Failed to read role cache, fallback to DB: userId={}", userId, e);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        boolean admin = user.getRole() != null && user.getRole() == ROLE_ADMIN;

        // 写入缓存（含负缓存：非管理员也缓存，防止穿透）
        try {
            redisTemplate.opsForValue().set(cacheKey, admin ? "1" : "0", CACHE_ROLE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to write role cache: userId={}", userId, e);
        }
        return admin;
    }

    /**
     * 清除用户角色缓存（管理员角色变更时调用）
     */
    public void invalidateRoleCache(Long userId) {
        try {
            redisTemplate.delete(CACHE_ROLE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Failed to invalidate role cache: userId={}", userId, e);
        }
    }
}
