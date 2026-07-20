package com.vidego.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String email;

    private String nickname;

    private String avatar;

    private String bio;

    @TableField("follower_count")
    private Integer followerCount;

    @TableField("following_count")
    private Integer followingCount;

    @TableField("video_count")
    private Integer videoCount;

    private Integer status;

    /**
     * 角色：0=普通用户 1=管理员
     */
    private Integer role;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
