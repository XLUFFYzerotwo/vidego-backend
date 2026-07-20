package com.vidego.module.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 视频人工审核请求体
 *
 * <p>用于 PUT /api/admin/videos/{videoId}/audit 接口</p>
 */
@Data
public class VideoAuditRequest {

    /**
     * 审核结果：1=通过，-1=不通过
     */
    @NotNull(message = "auditStatus is required (1=pass, -1=reject)")
    private Integer auditStatus;

    /**
     * 审核不通过原因（auditStatus=-1 时必填）
     */
    private String reason;
}
