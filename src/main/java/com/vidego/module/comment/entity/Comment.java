package com.vidego.module.comment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("video_id")
    private Long videoId;

    @TableField("user_id")
    private Long userId;

    @TableField("parent_id")
    private Long parentId;

    private String content;

    @TableField("like_count")
    private Integer likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
