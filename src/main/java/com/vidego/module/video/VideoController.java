package com.vidego.module.video;

import com.vidego.auth.UserContext;
import com.vidego.common.result.Result;
import com.vidego.module.video.dto.UploadTokenVO;
import com.vidego.module.video.dto.VideoCreateRequest;
import com.vidego.module.video.dto.VideoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Video", description = "Video upload, playback, and management")
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @Operation(summary = "Get presigned upload URL (call before uploading)")
    @GetMapping("/upload-token")
    public Result<UploadTokenVO> getUploadToken(
            @RequestParam String filename,
            @RequestParam long size) {
        return Result.success(videoService.getUploadToken(filename, size));
    }

    @Operation(summary = "Create video record (call after file is uploaded)")
    @PostMapping
    public Result<VideoVO> createVideo(@Valid @RequestBody VideoCreateRequest request) {
        return Result.success(videoService.createVideo(request));
    }

    @Operation(summary = "Get video details by ID (with Redis cache)")
    @GetMapping("/{videoId}")
    public Result<VideoVO> getVideo(@PathVariable Long videoId) {
        return Result.success(videoService.getVideoById(videoId));
    }

    @Operation(summary = "Record a view (with duplicate prevention)")
    @PostMapping("/{videoId}/view")
    public Result<Void> recordView(
            @PathVariable Long videoId,
            @Parameter(hidden = true) HttpServletRequest request) {
        String viewerId = resolveViewerId(request);
        videoService.recordView(videoId, viewerId);
        return Result.success();
    }

    @Operation(summary = "Get hot video ranking")
    @GetMapping("/hot")
    public Result<List<VideoVO>> getHotVideos(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(videoService.getHotVideos(limit));
    }

    @Operation(summary = "Delete a video (owner only)")
    @DeleteMapping("/{videoId}")
    public Result<Void> deleteVideo(@PathVariable Long videoId) {
        videoService.deleteVideo(videoId);
        return Result.success();
    }

    // ── 点赞 ──

    @Operation(summary = "Like a video")
    @PostMapping("/{videoId}/like")
    public Result<Void> likeVideo(@PathVariable Long videoId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.error(401, "unauthorized");
        videoService.likeVideo(videoId, userId);
        return Result.success();
    }

    @Operation(summary = "Unlike a video")
    @DeleteMapping("/{videoId}/like")
    public Result<Void> unlikeVideo(@PathVariable Long videoId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.error(401, "unauthorized");
        videoService.unlikeVideo(videoId, userId);
        return Result.success();
    }

    @Operation(summary = "Check if current user liked the video")
    @GetMapping("/{videoId}/like/status")
    public Result<Map<String, Boolean>> getLikeStatus(@PathVariable Long videoId) {
        Long userId = UserContext.getUserId();
        boolean liked = userId != null && videoService.getLikeStatus(videoId, userId);
        return Result.success(Map.of("liked", liked));
    }

    // ── 收藏 ──

    @Operation(summary = "Favorite a video")
    @PostMapping("/{videoId}/favorite")
    public Result<Void> favoriteVideo(@PathVariable Long videoId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.error(401, "unauthorized");
        videoService.favoriteVideo(videoId, userId);
        return Result.success();
    }

    @Operation(summary = "Unfavorite a video")
    @DeleteMapping("/{videoId}/favorite")
    public Result<Void> unfavoriteVideo(@PathVariable Long videoId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.error(401, "unauthorized");
        videoService.unfavoriteVideo(videoId, userId);
        return Result.success();
    }

    @Operation(summary = "Check if current user favorited the video")
    @GetMapping("/{videoId}/favorite/status")
    public Result<Map<String, Boolean>> getFavoriteStatus(@PathVariable Long videoId) {
        Long userId = UserContext.getUserId();
        boolean favorited = userId != null && videoService.getFavoriteStatus(videoId, userId);
        return Result.success(Map.of("favorited", favorited));
    }

    // ── 工具方法 ──

    /**
     * 解析观看者唯一标识。
     * 已登录 → "u:{userId}"，未登录 → "ip:{clientIp}"
     */
    private String resolveViewerId(HttpServletRequest request) {
        Long userId = com.vidego.auth.UserContext.getUserId();
        if (userId != null) {
            return "u:" + userId;
        }
        return "ip:" + getClientIp(request);
    }

    /**
     * 获取客户端真实 IP（支持反向代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For: client, proxy1, proxy2 → 取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
