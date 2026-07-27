package com.vidego.module.user.vo;

import lombok.Data;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String bio;
    private Integer followerCount;
    private Integer followingCount;
    private Integer videoCount;
    private Integer likeCount;
    /**
     * 角色：0=普通用户 1=管理员
     */
    private Integer role;
    private String createdAt;
    /**
     * 当前登录用户是否已关注该用户（未登录时为 false）
     */
    private Boolean isFollowing;
}
