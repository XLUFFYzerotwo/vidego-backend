package com.vidego.module.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidego.auth.UserContext;
import com.vidego.common.config.MinioConfig;
import com.vidego.common.exception.BusinessException;
import com.vidego.common.result.ErrorCode;
import com.vidego.module.user.entity.User;
import com.vidego.module.user.mapper.UserMapper;
import com.vidego.module.user.vo.UserVO;
import com.vidego.module.video.dto.UploadTokenVO;
import com.vidego.module.video.dto.VideoCreateRequest;
import com.vidego.module.video.dto.VideoVO;
import com.vidego.module.video.entity.Tag;
import com.vidego.module.video.entity.Video;
import com.vidego.module.video.entity.VideoTag;
import com.vidego.module.video.mapper.TagMapper;
import com.vidego.module.video.mapper.VideoMapper;
import com.vidego.module.video.mapper.VideoTagMapper;
import com.vidego.module.video.mapper.LikeRecordMapper;
import com.vidego.module.video.mapper.FavoriteMapper;
import com.vidego.module.video.entity.Favorite;
import com.vidego.module.video.entity.LikeRecord;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final VideoMapper videoMapper;
    private final TagMapper tagMapper;
    private final VideoTagMapper videoTagMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final FavoriteMapper favoriteMapper;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 允许上传的视频后缀 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "webm");

    /** 最大文件大小 500MB */
    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024L;

    /** 预签名 URL 有效期（分钟） */
    private static final int PRESIGNED_EXPIRY_MINUTES = 15;

    // ── Redis Key 前缀 ──
    private static final String CACHE_VIDEO_PREFIX = "vidego:cache:video:";
    private static final String VIEW_SET_PREFIX = "vidego:views:set:";
    private static final String CACHE_HOT_PREFIX = "vidego:cache:hot:videos";

    /** 视频详情缓存 TTL（秒） */
    private static final long CACHE_VIDEO_TTL = 600; // 10 分钟

    /** 观看去重集合 TTL（秒） */
    private static final long VIEW_SET_TTL = 86400; // 24 小时

    /** 热门视频缓存 TTL（秒） */
    private static final long CACHE_HOT_TTL = 300; // 5 分钟

    // ══════════════════════════════════════════════
    //  上传 & 创建（已有，无改动）
    // ══════════════════════════════════════════════

    @Override
    public UploadTokenVO getUploadToken(String filename, long fileSize) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String ext = extractExtension(filename);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        if (fileSize <= 0 || fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        String objectKey = userId + "/" + UUID.randomUUID() + "." + ext;
        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioConfig.getBucketVideo())
                            .object(objectKey)
                            .expiry(PRESIGNED_EXPIRY_MINUTES, TimeUnit.MINUTES)
                            .build());
            log.info("Upload token generated: userId={}, objectKey={}, size={}", userId, objectKey, fileSize);
            return new UploadTokenVO(uploadUrl, objectKey);
        } catch (Exception e) {
            log.error("Failed to generate upload token", e);
            throw new BusinessException(ErrorCode.UPLOAD_TOKEN_FAILED, e.getMessage());
        }
    }

    @Override
    @Transactional
    public VideoVO createVideo(VideoCreateRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        // 1. 校验视频文件已上传到 MinIO
        verifyVideoExists(request.getVideoKey());

        // 2. 防并发重复
        checkDuplicateVideoKey(request.getVideoKey());

        // 3. 生成封面
        String coverKey = generateCoverWithUrl(request.getVideoKey(), userId);

        // 4. 写入数据库
        Video video = new Video();
        video.setUserId(userId);
        video.setTitle(request.getTitle());
        video.setDescription(request.getDescription());
        video.setVideoKey(request.getVideoKey());
        video.setCoverKey(coverKey);
        video.setDuration(request.getDuration());
        video.setSize(request.getSize());
        video.setStatus(1);
        video.setViewCount(0);
        video.setLikeCount(0);
        video.setCommentCount(0);
        videoMapper.insert(video);

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            handleVideoTags(video.getId(), request.getTags());
        }

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .setSql("video_count = video_count + 1")
                .eq(User::getId, userId));

        log.info("Video created: id={}, title={}, userId={}",
                video.getId(), video.getTitle(), userId);
        return toVideoVO(video);
    }

    // ══════════════════════════════════════════════
    //  视频详情（带 Redis 缓存）
    // ══════════════════════════════════════════════

    @Override
    public VideoVO getVideoById(Long videoId) {
        // 1. 尝试从缓存读取
        String cacheKey = CACHE_VIDEO_PREFIX + videoId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, VideoVO.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize video cache, id={}", videoId, e);
                redisTemplate.delete(cacheKey);
            }
        }

        // 2. 缓存未命中，查询数据库
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        VideoVO vo = toVideoVO(video);

        // 3. 写入缓存（异步非关键，失败不影响返回）
        try {
            String json = objectMapper.writeValueAsString(vo);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_VIDEO_TTL, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize video cache, id={}", videoId, e);
        }

        return vo;
    }

    // ══════════════════════════════════════════════
    //  播放量记录（Redis 防刷）
    // ══════════════════════════════════════════════

    @Override
    public void recordView(Long videoId, String viewerId) {
        // 1. 检查视频是否存在
        if (!videoExists(videoId)) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        // 2. 去重：同一观看者在 24 小时内只算一次
        String today = LocalDate.now().toString();
        String setKey = VIEW_SET_PREFIX + videoId + ":" + today;

        Long added = redisTemplate.opsForSet().add(setKey, viewerId);
        if (added != null && added > 0) {
            // 新观看，设置 TTL 并更新数据库
            redisTemplate.expire(setKey, VIEW_SET_TTL, TimeUnit.SECONDS);
            videoMapper.incrementViewCount(videoId);

            // 使缓存中的播放量失效，下次请求重新加载
            redisTemplate.delete(CACHE_VIDEO_PREFIX + videoId);

            log.debug("View recorded: videoId={}, viewerId={}", videoId, viewerId);
        }
    }

    // ══════════════════════════════════════════════
    //  热门视频排行
    // ══════════════════════════════════════════════

    @Override
    public List<VideoVO> getHotVideos(int limit) {
        // 1. 尝试从缓存读取
        String cached = redisTemplate.opsForValue().get(CACHE_HOT_PREFIX);
        if (cached != null) {
            try {
                List<VideoVO> list = objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, VideoVO.class));
                // 缓存可能包含比请求更多的数据，截取
                return list.size() > limit ? list.subList(0, limit) : list;
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize hot video cache", e);
                redisTemplate.delete(CACHE_HOT_PREFIX);
            }
        }

        // 2. 查询数据库
        List<Video> videos = videoMapper.selectHotVideos(limit);
        List<VideoVO> vos = videos.stream().map(this::toVideoVO).collect(Collectors.toList());

        // 3. 写入缓存
        try {
            String json = objectMapper.writeValueAsString(vos);
            redisTemplate.opsForValue().set(CACHE_HOT_PREFIX, json, CACHE_HOT_TTL, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize hot video cache", e);
        }

        return vos;
    }

    // ══════════════════════════════════════════════
    //  点赞
    // ══════════════════════════════════════════════

    @Override
    @Transactional
    public void likeVideo(Long videoId, Long userId) {
        // 校验视频存在
        if (videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                .eq(Video::getId, videoId)) == 0) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        // 幂等
        Long count = likeRecordMapper.selectCount(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetType, "video")
                .eq(LikeRecord::getTargetId, videoId));
        if (count > 0) return;

        LikeRecord record = new LikeRecord();
        record.setUserId(userId);
        record.setTargetType("video");
        record.setTargetId(videoId);
        likeRecordMapper.insert(record);

        videoMapper.incrementLikeCount(videoId);
        redisTemplate.delete(CACHE_VIDEO_PREFIX + videoId);
        log.debug("Video liked: videoId={}, userId={}", videoId, userId);
    }

    @Override
    @Transactional
    public void unlikeVideo(Long videoId, Long userId) {
        int deleted = likeRecordMapper.delete(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetType, "video")
                .eq(LikeRecord::getTargetId, videoId));
        if (deleted > 0) {
            videoMapper.decrementLikeCount(videoId);
            redisTemplate.delete(CACHE_VIDEO_PREFIX + videoId);
            log.debug("Video unliked: videoId={}, userId={}", videoId, userId);
        }
    }

    @Override
    public boolean getLikeStatus(Long videoId, Long userId) {
        return likeRecordMapper.selectCount(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetType, "video")
                .eq(LikeRecord::getTargetId, videoId)) > 0;
    }

    // ══════════════════════════════════════════════
    //  收藏
    // ══════════════════════════════════════════════

    @Override
    @Transactional
    public void favoriteVideo(Long videoId, Long userId) {
        if (videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                .eq(Video::getId, videoId)) == 0) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        // 幂等
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getVideoId, videoId));
        if (count > 0) return;

        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setVideoId(videoId);
        favoriteMapper.insert(fav);
        log.debug("Video favorited: videoId={}, userId={}", videoId, userId);
    }

    @Override
    @Transactional
    public void unfavoriteVideo(Long videoId, Long userId) {
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getVideoId, videoId));
        log.debug("Video unfavorited: videoId={}, userId={}", videoId, userId);
    }

    @Override
    public boolean getFavoriteStatus(Long videoId, Long userId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getVideoId, videoId)) > 0;
    }

    // ══════════════════════════════════════════════
    //  删除（已有，微调使缓存失效）
    // ══════════════════════════════════════════════

    @Override
    @Transactional
    public void deleteVideo(Long videoId) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        Video video = videoMapper.selectById(videoId);
        if (video == null) throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        if (!video.getUserId().equals(userId)) throw new BusinessException(ErrorCode.NOT_VIDEO_OWNER);

        // 从 MinIO 删除文件
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucketVideo()).object(video.getVideoKey()).build());
            if (video.getCoverKey() != null) {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucketCover()).object(video.getCoverKey()).build());
            }
        } catch (Exception e) {
            log.warn("Failed to remove video files from MinIO, videoKey={}", video.getVideoKey(), e);
        }

        videoTagMapper.delete(new LambdaQueryWrapper<VideoTag>().eq(VideoTag::getVideoId, videoId));
        videoMapper.deleteById(videoId);

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .setSql("video_count = video_count - 1")
                .eq(User::getId, userId).gt(User::getVideoCount, 0));

        // 清除缓存
        redisTemplate.delete(CACHE_VIDEO_PREFIX + videoId);
        redisTemplate.delete(CACHE_HOT_PREFIX);

        log.info("Video deleted: id={}, userId={}", videoId, userId);
    }

    // ══════════════════════════════════════════════
    //  私有方法
    // ══════════════════════════════════════════════

    private boolean videoExists(Long videoId) {
        // 优先查缓存，次选查 DB
        String cacheKey = CACHE_VIDEO_PREFIX + videoId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            return true;
        }
        return videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                .eq(Video::getId, videoId)) > 0;
    }

    private void verifyVideoExists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getBucketVideo()).object(objectKey).build());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VIDEO_UPLOAD_FAILED,
                    "video file not found in storage, please upload first");
        }
    }

    /**
     * 校验 videoKey 不重复（防并发重复创建）
     */
    private void checkDuplicateVideoKey(String videoKey) {
        Long count = videoMapper.selectCount(
                new LambdaQueryWrapper<Video>().eq(Video::getVideoKey, videoKey));
        if (count > 0) {
            throw new BusinessException(ErrorCode.VIDEO_UPLOAD_FAILED,
                    "video with this key already exists");
        }
    }

    /**
     * 用 FFmpeg 通过 MinIO 预签名 URL 生成封面。
     * -ss 在 -i 之前（fast seek），MinIO 支持 HTTP Range 请求，无需下载整个文件。
     * 输出直接通过 image2pipe 读入内存，不落地磁盘。
     */
    private String generateCoverWithUrl(String videoKey, Long userId) {
        Process process = null;
        try {
            // 生成 10 分钟有效的预签名 GET URL（FFmpeg 处理通常只需几秒）
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucketVideo())
                            .object(videoKey)
                            .expiry(10, TimeUnit.MINUTES)
                            .build());

            String coverKey = userId + "/" + UUID.randomUUID() + ".jpg";

            // -ss 在 -i 之前 → fast seek（MinIO 支持 HTTP Range 请求）
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-ss", "00:00:01",
                    "-i", presignedUrl,
                    "-vframes", "1",
                    "-vf", "scale=320:-1",
                    "-f", "image2pipe",
                    "-"
            );
            pb.redirectErrorStream(true); // stderr → stdout，统一处理
            process = pb.start();

            // 读 FFmpeg stdout（JPEG 数据 + 可能的日志）
            ByteArrayOutputStream jpegBuf = new ByteArrayOutputStream();
            try (InputStream ffmpegOut = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = ffmpegOut.read(buf)) != -1) {
                    jpegBuf.write(buf, 0, n);
                }
            }

            int exitCode = process.waitFor(30, TimeUnit.SECONDS) ? process.exitValue() : -1;
            if (exitCode != 0) {
                process.destroyForcibly();
                // FFmpeg 正常退出但 exit code 非零，或超时
                String logMsg = exitCode == -1 ? "timed out" : ("exited with code " + exitCode);
                log.warn("FFmpeg {} for videoKey={}", logMsg, videoKey);
                return null;
            }

            // 从 stdout 提取 JPEG（跳过 FFmpeg 日志混杂问题，取末尾有效 JPEG 数据）
            if (jpegBuf.size() > 0) {
                byte[] jpegData = extractJpegFromBuffer(jpegBuf.toByteArray());
                if (jpegData == null) {
                    log.warn("No valid JPEG found in FFmpeg output for videoKey={}", videoKey);
                    return null;
                }

                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioConfig.getBucketCover())
                                .object(coverKey)
                                .stream(new ByteArrayInputStream(jpegData), jpegData.length, -1)
                                .contentType("image/jpeg")
                                .build());

                log.info("Cover generated via presigned URL: videoKey={}, coverKey={}",
                        videoKey, coverKey);
                return coverKey;
            }

        } catch (Exception e) {
            log.error("Cover generation failed for videoKey={}", videoKey, e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return null;
    }

    /**
     * 从 FFmpeg stdout 的混合输出中提取 JPEG 数据。
     * redirectErrorStream(true) 会把日志混入 stdout，JPEG 以 FFD8 开头、FFD9 结尾。
     * 取最后一个 FFD8...FFD9 段（FFmpeg 先打印日志再输出图片）。
     */
    private byte[] extractJpegFromBuffer(byte[] data) {
        // 查找最后一个 JPEG SOI (0xFF 0xD8)
        int start = -1;
        for (int i = data.length - 2; i >= 0; i--) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD8) {
                start = i;
                break;
            }
        }
        if (start < 0) return null;

        // 查找 JPEG EOI (0xFF 0xD9)
        int end = -1;
        for (int i = start + 2; i < data.length - 1; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD9) {
                end = i + 1;
            }
        }
        if (end < 0) return null;

        byte[] jpeg = new byte[end - start + 1];
        System.arraycopy(data, start, jpeg, 0, jpeg.length);
        return jpeg;
    }

    private void handleVideoTags(Long videoId, List<String> tagNames) {
        for (String name : tagNames) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, trimmed));
            if (tag == null) {
                tag = new Tag();
                tag.setName(trimmed);
                tagMapper.insert(tag);
            }
            VideoTag vt = new VideoTag();
            vt.setVideoId(videoId);
            vt.setTagId(tag.getId());
            videoTagMapper.insert(vt);
        }
    }

    private List<String> getVideoTagNames(Long videoId) {
        List<VideoTag> relations = videoTagMapper.selectList(
                new LambdaQueryWrapper<VideoTag>().eq(VideoTag::getVideoId, videoId));
        if (relations.isEmpty()) return List.of();
        Set<Long> tagIds = relations.stream().map(VideoTag::getTagId).collect(Collectors.toSet());
        return tagMapper.selectBatchIds(tagIds).stream().map(Tag::getName).collect(Collectors.toList());
    }

    private String extractExtension(String filename) {
        if (filename == null || filename.isEmpty()) return null;
        int dot = filename.lastIndexOf('.');
        return (dot == -1) ? null : filename.substring(dot + 1);
    }

    private VideoVO toVideoVO(Video video) {
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
        vo.setTags(getVideoTagNames(video.getId()));

        User user = userMapper.selectById(video.getUserId());
        if (user != null) {
            UserVO userVO = new UserVO();
            userVO.setId(user.getId());
            userVO.setUsername(user.getUsername());
            userVO.setNickname(user.getNickname());
            userVO.setAvatar(user.getAvatar());
            vo.setUser(userVO);
        }
        return vo;
    }
}
