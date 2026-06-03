package com.vidego.module.user;

import com.vidego.common.result.PageResult;
import com.vidego.module.user.dto.ChangePasswordRequest;
import com.vidego.module.user.dto.LoginRequest;
import com.vidego.module.user.dto.RegisterRequest;
import com.vidego.module.user.dto.UpdateUserRequest;
import com.vidego.module.user.vo.LoginVO;
import com.vidego.module.user.vo.UserVO;
import com.vidego.module.video.dto.VideoVO;

public interface UserService {

    LoginVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);

    LoginVO refresh(String refreshToken);

    void logout(String token);

    void changePassword(ChangePasswordRequest request);

    UserVO getUserById(Long userId);

    UserVO getCurrentUser();

    void follow(Long userId, Long targetUserId);

    void unfollow(Long userId, Long targetUserId);

    // ── 个人中心 ──

    /**
     * 更新个人资料
     */
    UserVO updateUser(Long userId, UpdateUserRequest request);

    /**
     * 获取用户的视频列表（我的投稿）
     */
    PageResult<VideoVO> getUserVideos(Long userId, int page, int size);

    /**
     * 获取用户点赞过的视频
     */
    PageResult<VideoVO> getLikedVideos(Long userId, int page, int size);

    /**
     * 获取用户收藏过的视频
     */
    PageResult<VideoVO> getFavoritedVideos(Long userId, int page, int size);

    /**
     * 获取关注列表
     */
    PageResult<UserVO> getFollowing(Long userId, int page, int size);

    /**
     * 获取粉丝列表
     */
    PageResult<UserVO> getFollowers(Long userId, int page, int size);

    /**
     * 上传头像到 MinIO 并更新用户信息
     */
    UserVO updateAvatar(Long userId, String filename, long size, byte[] imageData);
}
