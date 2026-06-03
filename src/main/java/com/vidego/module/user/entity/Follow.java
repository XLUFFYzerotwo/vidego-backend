package com.vidego.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("follow")
public class Follow {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("follower_id")
    private Long followerId;

    @TableField("following_id")
    private Long followingId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
