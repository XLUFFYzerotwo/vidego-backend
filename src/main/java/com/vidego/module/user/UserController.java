package com.vidego.module.user;

import com.vidego.auth.UserContext;
import com.vidego.common.result.PageResult;
import com.vidego.common.result.Result;
import com.vidego.module.user.dto.UpdateUserRequest;
import com.vidego.module.user.vo.UserVO;
import com.vidego.module.video.dto.VideoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "User", description = "User profile and personal center")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get user profile")
    @GetMapping("/{userId}")
    public Result<UserVO> getUser(@PathVariable Long userId) {
        return Result.success(userService.getUserById(userId));
    }

    @Operation(summary = "Update profile (login required)")
    @PutMapping("/{userId}")
    public Result<UserVO> updateUser(
            @PathVariable Long userId,
            @RequestBody UpdateUserRequest request) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) return Result.error(401, "unauthorized");
        if (!currentUserId.equals(userId)) return Result.error(403, "forbidden");
        return Result.success(userService.updateUser(userId, request));
    }

    @Operation(summary = "Get user's video list (my uploads)")
    @GetMapping("/{userId}/videos")
    public Result<PageResult<VideoVO>> getUserVideos(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getUserVideos(userId, page, size));
    }

    @Operation(summary = "Get user's liked videos")
    @GetMapping("/{userId}/likes")
    public Result<PageResult<VideoVO>> getLikedVideos(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getLikedVideos(userId, page, size));
    }

    @Operation(summary = "Get user's favorited videos")
    @GetMapping("/{userId}/favorites")
    public Result<PageResult<VideoVO>> getFavoritedVideos(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getFavoritedVideos(userId, page, size));
    }

    @Operation(summary = "Get following list")
    @GetMapping("/{userId}/following")
    public Result<PageResult<UserVO>> getFollowing(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getFollowing(userId, page, size));
    }

    @Operation(summary = "Get follower list")
    @GetMapping("/{userId}/followers")
    public Result<PageResult<UserVO>> getFollowers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getFollowers(userId, page, size));
    }

    @Operation(summary = "Follow a user")
    @PostMapping("/{userId}/follow")
    public Result<Void> follow(@PathVariable Long userId) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) return Result.error(401, "unauthorized");
        userService.follow(currentUserId, userId);
        return Result.success();
    }

    @Operation(summary = "Unfollow a user")
    @DeleteMapping("/{userId}/follow")
    public Result<Void> unfollow(@PathVariable Long userId) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) return Result.error(401, "unauthorized");
        userService.unfollow(currentUserId, userId);
        return Result.success();
    }

    @Operation(summary = "Upload avatar image")
    @PostMapping(value = "/{userId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserVO> uploadAvatar(
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) return Result.error(401, "unauthorized");
        if (!currentUserId.equals(userId)) return Result.error(403, "forbidden");

        try {
            byte[] data = file.getBytes();
            return Result.success(userService.updateAvatar(
                    userId, file.getOriginalFilename(), file.getSize(), data));
        } catch (Exception e) {
            return Result.error(500, "avatar upload failed: " + e.getMessage());
        }
    }
}
