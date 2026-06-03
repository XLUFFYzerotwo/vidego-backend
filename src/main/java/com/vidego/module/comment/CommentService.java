package com.vidego.module.comment;

import com.vidego.common.result.PageResult;
import com.vidego.module.comment.dto.CommentCreateRequest;
import com.vidego.module.comment.dto.CommentVO;

public interface CommentService {

    /**
     * 分页查询视频的根评论（每页 10 条），每条附带子回复
     */
    PageResult<CommentVO> getComments(Long videoId, int page, int size);

    /**
     * 发表评论或回复
     */
    CommentVO createComment(Long videoId, Long userId, CommentCreateRequest request);

    /**
     * 删除评论（仅作者可删，根评论会连带删除子回复）
     */
    void deleteComment(Long commentId, Long userId);

    /**
     * 点赞评论
     */
    void likeComment(Long commentId, Long userId);

    /**
     * 取消点赞评论
     */
    void unlikeComment(Long commentId, Long userId);
}
