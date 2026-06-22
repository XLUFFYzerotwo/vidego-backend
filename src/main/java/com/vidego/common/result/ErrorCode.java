package com.vidego.common.result;

public enum ErrorCode {

    // ── 通用 ──
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    NOT_FOUND(404, "not found"),
    INTERNAL_ERROR(500, "internal error"),

    // ── 业务：用户模块 1001-1999 ──
    USERNAME_EXISTS(1001, "username already exists"),
    EMAIL_EXISTS(1002, "email already exists"),
    USER_NOT_FOUND(1003, "user not found"),
    INVALID_CREDENTIALS(1004, "invalid username or password"),
    PASSWORD_MISMATCH(1005, "passwords do not match"),
    INVALID_TOKEN(1006, "invalid or expired token"),
    TOKEN_EXPIRED(1007, "token expired"),
    USER_DISABLED(1008, "account has been disabled"),
    USER_SEND_FREQUENTLY(1009, "user send frequently"),

    // ── 业务：视频模块 2001-2999 ──
    VIDEO_NOT_FOUND(2001, "video not found"),
    FILE_TYPE_NOT_ALLOWED(2002, "file type not allowed, only MP4 and WebM are accepted"),
    FILE_SIZE_EXCEEDED(2003, "file size exceeds the maximum limit of 500MB"),
    VIDEO_PROCESSING_FAILED(2004, "video processing failed"),
    UPLOAD_TOKEN_FAILED(2005, "failed to generate upload token"),
    VIDEO_UPLOAD_FAILED(2006, "video upload verification failed"),
    NOT_VIDEO_OWNER(2007, "you are not the owner of this video"),
    TAG_NOT_FOUND(2008, "tag not found");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
