package com.vidego.module.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final TagMapper tagMapper;
    private final VideoTagMapper videoTagMapper;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** FULLTEXT 搜索模式匹配时的最小有效关键词长度 */
    private static final int MIN_KEYWORD_LENGTH = 2;

    @Override
    public PageResult<VideoVO> search(String keyword, int page, int size) {
        // 校验关键词
        if (!StringUtils.hasText(keyword)) {
            return new PageResult<>(Collections.emptyList(), 0, page, size);
        }

        String trimmed = keyword.trim();

        // 关键词过短时退化为 LIKE 模糊匹配
        if (trimmed.length() < MIN_KEYWORD_LENGTH) {
            return searchByLike(trimmed, page, size);
        }

        // 使用 MySQL FULLTEXT 索引搜索（BOOLEAN MODE 支持 + - * 语法）
        try {
            Page<Video> videoPage = videoMapper.selectPage(
                    new Page<>(page, size),
                    new LambdaQueryWrapper<Video>()
                            .eq(Video::getStatus, 1)
                            .eq(Video::getAuditStatus, 1)
                            .apply("MATCH(title, description) AGAINST({0} IN BOOLEAN MODE)", trimmed));

            if (!videoPage.getRecords().isEmpty()) {
                List<VideoVO> vos = videoPage.getRecords().stream()
                        .map(this::toVideoVO)
                        .collect(Collectors.toList());
                return new PageResult<>(vos, videoPage.getTotal(),
                        (int) videoPage.getCurrent(), (int) videoPage.getSize());
            }
        } catch (Exception e) {
            log.warn("FULLTEXT search failed, falling back to LIKE: keyword={}", trimmed, e);
        }

        // FULLTEXT 无结果或出错时降级到 LIKE 模糊搜索
        return searchByLike(trimmed, page, size);
    }

    // ══════════════════════════════════════════════
    //  内部方法
    // ══════════════════════════════════════════════

    /**
     * LIKE 模糊搜索（兜底方案）
     * 仅返回已发布（status=1）且审核通过（audit_status=1）的视频
     */
    private PageResult<VideoVO> searchByLike(String keyword, int page, int size) {
        Page<Video> videoPage = videoMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getStatus, 1)
                        .eq(Video::getAuditStatus, 1)
                        .and(w -> w.like(Video::getTitle, keyword)
                                .or()
                                .like(Video::getDescription, keyword))
                        .orderByDesc(Video::getCreatedAt));

        List<VideoVO> vos = videoPage.getRecords().stream()
                .map(this::toVideoVO)
                .collect(Collectors.toList());

        return new PageResult<>(vos, videoPage.getTotal(),
                (int) videoPage.getCurrent(), (int) videoPage.getSize());
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
