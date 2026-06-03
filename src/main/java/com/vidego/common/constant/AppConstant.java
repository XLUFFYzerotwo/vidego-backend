package com.vidego.common.constant;

public class AppConstant {

    private AppConstant() {}

    /**
     * 认证相关
     */
    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * 用户状态
     */
    public static final int USER_STATUS_NORMAL = 1;
    public static final int USER_STATUS_DISABLED = 0;

    /**
     * 文件大小限制 (500MB)
     */
    public static final long MAX_FILE_SIZE = 500 * 1024 * 1024L;
}
