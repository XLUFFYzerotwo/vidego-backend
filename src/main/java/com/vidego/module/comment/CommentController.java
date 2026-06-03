package com.vidego.module.comment;

import com.vidego.auth.UserContext;
import com.vidego.common.result.PageResult;
import com.vidego.common.result.Result;
import com.vidego.module.comment.dto.CommentCreateRequest;
import com.vidego.module.comment.dto.CommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Comment", description = "Comment and reply management")
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "Get paginated comments for a video")
    @GetMapping("/api/videos/{videoId}/comments")
    public Result<PageResult<CommentVO>> getComments(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(commentService.getComments(videoId, page, size));
    }

    @Operation(summary = "Create a comment or reply")
    @PostMapping("/api/videos/{videoId}/comments")
    public Result<CommentVO> createComment(
            @PathVariable Long videoId,
            @Valid @RequestBody CommentCreateRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(401, "unauthorized");
        }
        return Result.success(commentService.createComment(videoId, userId, request));
    }

    @Operation(summary = "Delete a comment (owner only)")
    @DeleteMapping("/api/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(401, "unauthorized");
        }
        commentService.deleteComment(commentId, userId);
        return Result.success();
    }

    @Operation(summary = "Like a comment")
    @PostMapping("/api/comments/{commentId}/like")
    public Result<Void> likeComment(@PathVariable Long commentId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(401, "unauthorized");
        }
        commentService.likeComment(commentId, userId);
        return Result.success();
    }

    @Operation(summary = "Unlike a comment")
    @DeleteMapping("/api/comments/{commentId}/like")
    public Result<Void> unlikeComment(@PathVariable Long commentId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(401, "unauthorized");
        }
        commentService.unlikeComment(commentId, userId);
        return Result.success();
    }
}
