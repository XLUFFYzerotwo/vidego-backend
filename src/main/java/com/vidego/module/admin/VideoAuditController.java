package com.vidego.module.admin;

import com.vidego.auth.RequireAdmin;
import com.vidego.auth.UserContext;
import com.vidego.common.exception.BusinessException;
import com.vidego.common.result.ErrorCode;
import com.vidego.common.result.PageResult;
import com.vidego.common.result.Result;
import com.vidego.module.admin.dto.VideoAuditRequest;
import com.vidego.module.video.VideoService;
import com.vidego.module.video.dto.VideoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 视频人工审核接口（管理员）
 *
 * <p>提供：</p>
 * <ul>
 *   <li>PUT /api/admin/videos/{videoId}/audit —— 人工审核（通过 / 驳回）</li>
 *   <li>GET /api/admin/videos/audit-pending —— 获取待审核视频列表</li>
 * </ul>
 *
 * <p>权限校验：标注 {@link RequireAdmin}，
 * 由 {@link com.vidego.auth.AdminAuthAspect} 切面拦截，仅 role=1 的管理员可访问。</p>
 */
@Tag(name = "Admin-VideoAudit", description = "Administrator video audit APIs")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireAdmin
public class VideoAuditController {

    private final VideoService videoService;

    /**
     * 人工审核视频（通过 / 驳回）
     *
     * @param videoId 视频 ID
     * @param request 审核请求体（auditStatus: 1=通过 -1=不通过；reason: 不通过原因）
     */
    @Operation(summary = "Audit a video (admin only)")
    @PutMapping("/videos/{videoId}/audit")
    public Result<Void> auditVideo(@PathVariable Long videoId,
                                   @Valid @RequestBody VideoAuditRequest request) {
        // 当前用户身份已由 AdminAuthAspect 校验通过，这里直接读取 userId
        Long auditorId = UserContext.getUserId();

        // 不通过时必须填写原因
        if (request.getAuditStatus() == -1
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "reason is required when rejecting");
        }

        videoService.updateAuditStatus(videoId, request.getAuditStatus(), request.getReason(), auditorId);
        return Result.success();
    }

    /**
     * 获取待审核视频列表（分页）
     */
    @Operation(summary = "List pending audit videos (admin only)")
    @GetMapping("/videos/audit-pending")
    public Result<PageResult<VideoVO>> getAuditPendingVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(videoService.getAuditPendingVideos(page, size));
    }
}
