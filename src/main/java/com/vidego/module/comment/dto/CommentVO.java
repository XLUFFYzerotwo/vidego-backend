package com.vidego.module.comment.dto;

import com.vidego.module.user.vo.UserVO;
import lombok.Data;

import java.util.List;

@Data
public class CommentVO {

    private Long id;
    private Long videoId;
    private Long userId;
    private Long parentId;
    private String content;
    private Integer likeCount;
    private String createdAt;

    /** 评论作者信息 */
    private UserVO user;

    /** 子回复（仅根评论有此字段） */
    private List<CommentVO> replies;
}
