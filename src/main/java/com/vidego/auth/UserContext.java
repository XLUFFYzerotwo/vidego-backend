package com.vidego.auth;

/**
 * 持有当前请求的用户信息 (ThreadLocal)
 */
public class UserContext {

    private static final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        userIdHolder.set(userId);
    }

    public static Long getUserId() {
        return userIdHolder.get();
    }

    public static void clear() {
        userIdHolder.remove();
    }
}
