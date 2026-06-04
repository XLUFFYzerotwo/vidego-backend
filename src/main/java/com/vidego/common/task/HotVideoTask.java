package com.vidego.common.task;

import com.vidego.module.video.entity.Video;
import com.vidego.module.video.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotVideoTask {

    private final StringRedisTemplate redisTemplate;
    private final VideoMapper videoMapper;

    private static final String CACHE_HOT_PREFIX = "vidego:cache:hot:videos";

    /**
     * 每 3 小时刷新热门视频评分。
     * 根据播放量、点赞数、评论数重新计算 hot_score，不清除旧值。
     */
    @Scheduled(cron = "0 0 */3 * * ?")
    public void processHotVideo() {
        log.info("HotVideo task started: {}", new Date());

        List<Video> videos = videoMapper.selectHotVideos(100);
        for (Video video : videos) {
            int score = (int) (video.getViewCount() * 0.7
                    + video.getLikeCount() * 0.2
                    + video.getCommentCount() * 0.1);
            video.setHotScore(score);
            videoMapper.updateById(video);
        }

        // 清除热门缓存，下次请求重新从 DB 读取
        redisTemplate.delete(CACHE_HOT_PREFIX);

        log.info("HotVideo task finished: {} videos updated", videos.size());
    }
}
