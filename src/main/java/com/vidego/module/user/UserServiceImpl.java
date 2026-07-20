package com.vidego.module.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vidego.auth.JwtUtil;
import com.vidego.auth.TokenService;
import com.vidego.auth.UserContext;
import com.vidego.common.exception.BusinessException;
import com.vidego.common.result.ErrorCode;
import com.vidego.common.result.PageResult;
import com.vidego.common.config.MinioConfig;
import com.vidego.module.user.dto.ChangePasswordRequest;
import com.vidego.module.user.dto.LoginRequest;
import com.vidego.module.user.dto.RegisterRequest;
import com.vidego.module.user.dto.UpdateUserRequest;
import com.vidego.module.user.vo.LoginVO;
import com.vidego.module.user.vo.UserVO;
import com.vidego.module.user.entity.User;
import com.vidego.module.user.mapper.UserMapper;
import com.vidego.module.user.entity.Follow;
import com.vidego.module.user.mapper.FollowMapper;
import com.vidego.module.video.dto.VideoVO;
import com.vidego.module.video.entity.LikeRecord;
import com.vidego.module.video.entity.Video;
import com.vidego.module.video.mapper.LikeRecordMapper;
import com.vidego.module.video.mapper.FavoriteMapper;
import com.vidego.module.video.entity.Favorite;
import com.vidego.module.video.mapper.VideoMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final FollowMapper followMapper;
    private final VideoMapper videoMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final FavoriteMapper favoriteMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public LoginVO register(RegisterRequest request) {
        // 校验两次密码一致
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 检查用户名唯一
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // 检查邮箱唯一
        if (StringUtils.hasText(request.getEmail())) {
            if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, request.getEmail())) > 0) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setNickname(request.getUsername());
        user.setStatus(1);
        user.setFollowerCount(0);
        user.setFollowingCount(0);
        user.setVideoCount(0);
        userMapper.insert(user);

        log.info("User registered: id={}, username={}", user.getId(), user.getUsername());

        return buildLoginVO(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        // 支持用户名或邮箱登录
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getAccount())
                .or()
                .eq(User::getEmail, request.getAccount()));

        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        log.info("User logged in: id={}, username={}", user.getId(), user.getUsername());
        return buildLoginVO(user);
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 刷新 token 时，原有的 refresh token 作废
        tokenService.blacklist(refreshToken);

        return buildLoginVO(user);
    }

    @Override
    public void logout(String token) {
        tokenService.blacklist(token);
        log.info("User logged out: userId={}", UserContext.getUserId());
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 校验两次新密码一致
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "old password is incorrect");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);

        log.info("Password changed: userId={}", userId);
    }

    @Override
    public UserVO getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toUserVO(user);
    }

    @Override
    public UserVO getCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return getUserById(userId);
    }

    @Override
    @Transactional
    public UserVO updateUser(Long userId, UpdateUserRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);

        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        if (StringUtils.hasText(request.getBio())) {
            user.setBio(request.getBio());
        }
        if (StringUtils.hasText(request.getEmail())) {
            user.setEmail(request.getEmail());
        }
        if (StringUtils.hasText(request.getAvatar())) {
            user.setAvatar(request.getAvatar());
        }
        userMapper.updateById(user);
        log.info("User updated: id={}", userId);
        return toUserVO(user);
    }

    @Override
    public UserVO updateAvatar(Long userId, String filename, long size, byte[] imageData) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);

        // 校验文件类型
        String ext = extractImageExt(filename);
        if (ext == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "only JPG, PNG, WebP images are allowed");
        }
        if (size > 5 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED,
                    "avatar must be less than 5MB");
        }

        try {
            // 1. 删除旧头像（如果存在）
            String oldAvatar = user.getAvatar();
            if (oldAvatar != null && oldAvatar.startsWith("/storage/avatars/")) {
                String oldKey = oldAvatar.replaceFirst("/storage/avatars/", "");
                try {
                    minioClient.removeObject(
                            io.minio.RemoveObjectArgs.builder()
                                    .bucket(minioConfig.getBucketAvatar())
                                    .object(oldKey)
                                    .build());
                    log.info("Deleted old avatar: key={}", oldKey);
                } catch (Exception e) {
                    log.warn("Failed to delete old avatar, may already be removed: key={}", oldKey);
                }
            }

            // 2. 上传新头像到 MinIO（统一 key 格式: {userId}/{uuid}.{ext}）
            String objectKey = userId + "/" + UUID.randomUUID() + "." + ext;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketAvatar())
                            .object(objectKey)
                            .stream(new java.io.ByteArrayInputStream(imageData),
                                    imageData.length, -1)
                            .contentType("image/" + (ext.equals("jpg") ? "jpeg" : ext))
                            .build());

            // 更新用户头像字段（完整 URL 路径）
            user.setAvatar("/storage/avatars/" + objectKey);
            userMapper.updateById(user);

            log.info("Avatar updated: userId={}, key={}", userId, objectKey);
            return toUserVO(user);

        } catch (Exception e) {
            log.error("Failed to upload avatar for userId={}", userId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "avatar upload failed");
        }
    }

    @Override
    public PageResult<VideoVO> getUserVideos(Long userId, int page, int size) {
        // 限制最大翻页深度
        page = Math.min(page, 100);
        Page<Video> videoPage = videoMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getUserId, userId)
                        .eq(Video::getStatus, 1)
                        .orderByDesc(Video::getCreatedAt));

        List<VideoVO> vos = videoPage.getRecords().stream()
                .map(this::toSimpleVideoVO)
                .collect(Collectors.toList());
        return new PageResult<>(vos, videoPage.getTotal(),
                (int) videoPage.getCurrent(), (int) videoPage.getSize());
    }

    @Override
    public PageResult<VideoVO> getLikedVideos(Long userId, int page, int size) {
        // 限制最大翻页深度
        page = Math.min(page, 100);
        // 分页查询点赞记录
        Page<LikeRecord> likePage = likeRecordMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getUserId, userId)
                        .eq(LikeRecord::getTargetType, "video")
                        .orderByDesc(LikeRecord::getCreatedAt));

        if (likePage.getRecords().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, size);
        }

        // 批量查视频
        Set<Long> videoIds = likePage.getRecords().stream()
                .map(LikeRecord::getTargetId).collect(Collectors.toSet());
        List<Video> videos = videoMapper.selectBatchIds(videoIds);

        Map<Long, Video> videoMap = videos.stream()
                .collect(Collectors.toMap(Video::getId, v -> v, (a, b) -> a));

        List<VideoVO> vos = likePage.getRecords().stream()
                .map(r -> videoMap.get(r.getTargetId()))
                .filter(Objects::nonNull)
                .map(this::toSimpleVideoVO)
                .collect(Collectors.toList());

        return new PageResult<>(vos, likePage.getTotal(),
                (int) likePage.getCurrent(), (int) likePage.getSize());
    }

    @Override
    public PageResult<VideoVO> getFavoritedVideos(Long userId, int page, int size) {
        // 限制最大翻页深度
        page = Math.min(page, 100);
        Page<Favorite> favPage = favoriteMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreatedAt));

        if (favPage.getRecords().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, size);
        }

        Set<Long> videoIds = favPage.getRecords().stream()
                .map(Favorite::getVideoId).collect(Collectors.toSet());
        List<Video> videos = videoMapper.selectBatchIds(videoIds);
        Map<Long, Video> videoMap = videos.stream()
                .collect(Collectors.toMap(Video::getId, v -> v, (a, b) -> a));

        List<VideoVO> vos = favPage.getRecords().stream()
                .map(f -> videoMap.get(f.getVideoId()))
                .filter(Objects::nonNull)
                .map(this::toSimpleVideoVO)
                .collect(Collectors.toList());

        return new PageResult<>(vos, favPage.getTotal(),
                (int) favPage.getCurrent(), (int) favPage.getSize());
    }

    @Override
    public PageResult<UserVO> getFollowing(Long userId, int page, int size) {
        // 限制最大翻页深度
        page = Math.min(page, 100);
        Page<Follow> followPage = followMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, userId)
                        .orderByDesc(Follow::getCreatedAt));

        if (followPage.getRecords().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, size);
        }

        Set<Long> ids = followPage.getRecords().stream()
                .map(Follow::getFollowingId).collect(Collectors.toSet());
        List<User> users = userMapper.selectBatchIds(ids);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        Function.identity(),
                        (a, b) -> a
                ));

        List<UserVO> vos = followPage.getRecords().stream()
                .map(f -> userMap.get(f.getFollowingId()))
                .filter(Objects::nonNull)
                .map(this::toUserVO)
                .collect(Collectors.toList());

        return new PageResult<>(vos, followPage.getTotal(),
                (int) followPage.getCurrent(), (int) followPage.getSize());
    }

    @Override
    public PageResult<UserVO> getFollowers(Long userId, int page, int size) {
        // 限制最大翻页深度
        page = Math.min(page, 100);
        Page<Follow> followPage = followMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowingId, userId)
                        .orderByDesc(Follow::getCreatedAt));

        if (followPage.getRecords().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, size);
        }

        Set<Long> ids = followPage.getRecords().stream()
                .map(Follow::getFollowerId).collect(Collectors.toSet());
        List<User> users = userMapper.selectBatchIds(ids);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        Function.identity(),
                        (a, b) -> a)
                );

        List<UserVO> vos = followPage.getRecords().stream()
                .map(f -> userMap.get(f.getFollowerId()))
                .filter(Objects::nonNull)
                .map(this::toUserVO)
                .collect(Collectors.toList());

        return new PageResult<>(vos, followPage.getTotal(),
                (int) followPage.getCurrent(), (int) followPage.getSize());
    }

    // ── 私有方法 ──

    private LoginVO buildLoginVO(User user) {
        String token = jwtUtil.generateAccessToken(user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        return new LoginVO(token, refreshToken, toUserVO(user));
    }

    @Override
    @Transactional
    public void follow(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "cannot follow yourself");
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);

        // 幂等
        Long count = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId)
                .eq(Follow::getFollowingId, targetUserId));
        if (count > 0) return;

        Follow follow = new Follow();
        follow.setFollowerId(userId);
        follow.setFollowingId(targetUserId);
        followMapper.insert(follow);

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .setSql("following_count = following_count + 1").eq(User::getId, userId));
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .setSql("follower_count = follower_count + 1").eq(User::getId, targetUserId));
        log.info("User {} followed {}", userId, targetUserId);
    }

    @Override
    @Transactional
    public void unfollow(Long userId, Long targetUserId) {
        int deleted = followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId)
                .eq(Follow::getFollowingId, targetUserId));
        if (deleted > 0) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .setSql("following_count = following_count - 1").eq(User::getId, userId)
                    .gt(User::getFollowingCount, 0));
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .setSql("follower_count = follower_count - 1").eq(User::getId, targetUserId)
                    .gt(User::getFollowerCount, 0));
            log.info("User {} unfollowed {}", userId, targetUserId);
        }
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setBio(user.getBio());
        vo.setFollowerCount(user.getFollowerCount());
        vo.setFollowingCount(user.getFollowingCount());
        vo.setVideoCount(user.getVideoCount());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().format(DTF) : null);
        return vo;
    }

    private String extractImageExt(String filename) {
        if (filename == null || filename.isEmpty()) return null;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "jpg";
        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".webp")) return "webp";
        return null;
    }

    private VideoVO toSimpleVideoVO(Video video) {
        if (video == null) return null;
        VideoVO vo = new VideoVO();
        vo.setId(video.getId());
        vo.setUserId(video.getUserId());
        vo.setTitle(video.getTitle());
        vo.setDescription(video.getDescription());
        vo.setVideoUrl("/storage/videos/" + video.getVideoKey()); 
        vo.setCoverUrl(video.getCoverKey() != null ? "/storage/covers/" + video.getCoverKey() : null);
        vo.setDuration(video.getDuration());
        vo.setSize(video.getSize());
        vo.setStatus(video.getStatus());
        vo.setViewCount(video.getViewCount());
        vo.setLikeCount(video.getLikeCount());
        vo.setCommentCount(video.getCommentCount());
        vo.setCreatedAt(video.getCreatedAt() != null ? video.getCreatedAt().format(DTF) : null);
        // 填充用户信息（个人中心视频卡片需要显示作者头像）
        User author = userMapper.selectById(video.getUserId());
        if (author != null) {
            com.vidego.module.user.vo.UserVO authorVO = new com.vidego.module.user.vo.UserVO();
            authorVO.setId(author.getId());
            authorVO.setUsername(author.getUsername());
            authorVO.setNickname(author.getNickname());
            authorVO.setAvatar(author.getAvatar());
            vo.setUser(authorVO);
        }
        return vo;
    }
}
