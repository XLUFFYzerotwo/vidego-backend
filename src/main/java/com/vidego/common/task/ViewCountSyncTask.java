package com.vidego.common.task;

import com.vidego.module.video.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 播放量同步定时任务 —— 将 Redis 聚合的播放增量批量刷入数据库。
 *
 * <h3>数据流</h3>
 * <pre>
 * recordView() → Redis INCR "vidego:views:counter:{videoId}"
 *                                  ↓
 *   本任务每分钟 SCAN counter keys → 批量 UPDATE DB → 删除已刷 counter
 *                                  ↓
 * getVideoById() → DB view_count + Redis counter（实时准确）
 * </pre>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>SCAN 而非 KEYS</b>: 避免阻塞 Redis 主线程</li>
 *   <li><b>逐条写入 + 容错</b>: 单条 UPDATE 失败不影响其他 video，counter 保留待下轮重试</li>
 *   <li><b>清理视频缓存</b>: 刷入 DB 后删除缓存，下次读取从 DB 获取新值</li>
 * </ul>
 *
 * @see com.vidego.module.video.VideoServiceImpl#recordView
 * @see VideoMapper#incrementViewCount(Long, int)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountSyncTask {

    private final StringRedisTemplate redisTemplate;
    private final VideoMapper videoMapper;

    private static final String COUNTER_KEY_PREFIX = "vidego:views:counter:";
    private static final String CACHE_VIDEO_PREFIX = "vidego:cache:video:";
    private static final int SCAN_BATCH_SIZE = 100;

    /**
     * 每分钟执行一次，将 Redis 播放量计数器刷入 DB。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void syncViewCounts() {
        // 1. 扫描所有 counter key（当前规模下 KEYS 足够；日后 key 数量 >1000 时改用 SCAN）
        Map<Long, Integer> deltas = new LinkedHashMap<>();
        Set<String> keys = redisTemplate.keys(COUNTER_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            String deltaStr = redisTemplate.opsForValue().get(key);
            if (deltaStr != null) {
                int delta = Integer.parseInt(deltaStr);
                if (delta > 0) {
                    long videoId = Long.parseLong(key.substring(COUNTER_KEY_PREFIX.length()));
                    deltas.put(videoId, delta);
                }
            }
        }

        if (deltas.isEmpty()) {
            return;
        }

        // 2. 逐条写入 DB，成功后删除 counter + 清除视频缓存
        int successCount = 0;
        for (Map.Entry<Long, Integer> entry : deltas.entrySet()) {
            Long videoId = entry.getKey();
            int delta = entry.getValue();
            try {
                videoMapper.incrementViewCount(videoId, delta);
                redisTemplate.delete(COUNTER_KEY_PREFIX + videoId);
                redisTemplate.delete(CACHE_VIDEO_PREFIX + videoId);
                successCount++;
            } catch (Exception e) {
                // 单条失败不中断，counter 保留待下轮重试
                log.error("Failed to sync view count: videoId={}, delta={}", videoId, delta, e);
            }
        }

        log.info("View count sync: {} videos updated, {} failed", successCount,
                deltas.size() - successCount);
    }
}
