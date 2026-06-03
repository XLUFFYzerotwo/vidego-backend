package com.vidego.module.feed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidego.common.exception.BusinessException;
import com.vidego.common.result.ErrorCode;
import com.vidego.common.result.PageResult;
import com.vidego.module.user.entity.User;
import com.vidego.module.user.mapper.UserMapper;
import com.vidego.module.user.vo.UserVO;
import com.vidego.module.video.dto.VideoVO;
import com.vidego.module.video.entity.Tag;
import com.vidego.module.video.entity.Video;
import com.vidego.module.video.entity.VideoTag;
import com.vidego.module.video.mapper.TagMapper;
import com.vidego.module.video.mapper.VideoMapper;
import com.vidego.module.video.mapper.VideoTagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final TagMapper tagMapper;
    private final VideoTagMapper videoTagMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── Redis 缓存配置 ──
    private static final String CACHE_RECOMMENDED_PREFIX = "vidego:cache:feed:recommended:page1";
    private static final String CACHE_LATEST_PREFIX = "vidego:cache:feed:latest:page1";
    private static final long CACHE_RECOMMENDED_TTL = 60;   // 1 分钟
    private static final long CACHE_LATEST_TTL = 30;        // 30 秒

    @Override
    public PageResult<VideoVO> getRecommendedFeed(int page, int size) {
        // 第 1 页：走缓存，默认 20 条
        if (page == 1) {
            PageResult<VideoVO> cached = tryGetFromCache(CACHE_RECOMMENDED_PREFIX);
            if (cached != null) return cached;
        }

        // 查询数据库（已发布状态，按创建时间降序）
        PageResult<VideoVO> result = queryLatestVideos(page, size);

        // 第 1 页写入缓存
        if (page == 1) {
            putToCache(CACHE_RECOMMENDED_PREFIX, result, CACHE_RECOMMENDED_TTL);
        }

        return result;
    }

    @Override
    public PageResult<VideoVO> getLatestVideos(int page, int size) {
        // 第 1 页走缓存
        if (page == 1) {
            PageResult<VideoVO> cached = tryGetFromCache(CACHE_LATEST_PREFIX);
            if (cached != null) return cached;
        }

        PageResult<VideoVO> result = queryLatestVideos(page, size);

        if (page == 1) {
            putToCache(CACHE_LATEST_PREFIX, result, CACHE_LATEST_TTL);
        }

        return result;
    }

    @Override
    public PageResult<VideoVO> getVideosByTag(String tagName, int page, int size) {
        // 查询标签
        Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, tagName));
        if (tag == null) {
            return new PageResult<>(Collections.emptyList(), 0, page, size);
        }

        // 分页查询该标签下的视频 ID
        Page<VideoTag> relationPage = videoTagMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<VideoTag>()
                        .eq(VideoTag::getTagId, tag.getId())
                        .orderByDesc(VideoTag::getId));
        // 一个 VideoTag 自增 ID 近似反映关联时间

        if (relationPage.getRecords().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, size);
        }

        // 批量查询视频详情
        Set<Long> videoIds = relationPage.getRecords().stream()
                .map(VideoTag::getVideoId).collect(Collectors.toSet());
        List<Video> videos = videoMapper.selectBatchIds(videoIds);

        // 转换为 VideoVO（保持分页顺序）
        List<VideoVO> vos = videos.stream()
                .map(this::toVideoVO)
                .collect(Collectors.toList());

        return new PageResult<>(vos, relationPage.getTotal(), page, size);
    }

    // ══════════════════════════════════════════════
    //  内部方法
    // ══════════════════════════════════════════════

    /**
     * 按最新发布分页查询已发布的视频
     */
    private PageResult<VideoVO> queryLatestVideos(int page, int size) {
        Page<Video> videoPage = videoMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getStatus, 1)
                        .orderByDesc(Video::getCreatedAt));

        List<VideoVO> vos = videoPage.getRecords().stream()
                .map(this::toVideoVO)
                .collect(Collectors.toList());

        return new PageResult<>(vos, videoPage.getTotal(), (int) videoPage.getCurrent(), (int) videoPage.getSize());
    }

    // ── Redis 缓存 ──

    @SuppressWarnings("unchecked")
    private PageResult<VideoVO> tryGetFromCache(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, PageResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize feed cache: key={}", key, e);
            redisTemplate.delete(key);
            return null;
        }
    }

    private void putToCache(String key, PageResult<VideoVO> result, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize feed cache: key={}", key, e);
        }
    }

    // ── VideoVO 构建 ──

    private VideoVO toVideoVO(Video video) {
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

        // 标签
        List<VideoTag> relations = videoTagMapper.selectList(
                new LambdaQueryWrapper<VideoTag>().eq(VideoTag::getVideoId, video.getId()));
        if (!relations.isEmpty()) {
            Set<Long> tagIds = relations.stream().map(VideoTag::getTagId).collect(Collectors.toSet());
            List<Tag> tags = tagMapper.selectBatchIds(tagIds);
            vo.setTags(tags.stream().map(Tag::getName).collect(Collectors.toList()));
        }

        // 作者
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
