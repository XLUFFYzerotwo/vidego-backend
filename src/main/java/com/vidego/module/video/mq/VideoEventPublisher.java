package com.vidego.module.video.mq;

import com.vidego.common.config.RabbitMqConfig;
import com.vidego.module.video.dto.VideoProcessMessage;
import com.vidego.module.video.entity.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 视频事件发布器 —— 封装向 RabbitMQ 发送消息的逻辑
 *
 * <p>在 {@code VideoServiceImpl.createVideo()} 的事务提交后调用此发布器，
 * 将视频创建事件异步通知各消费者。</p>
 *
 * <h3>使用方式（在 VideoServiceImpl 中）：</h3>
 * <pre>
 * // 视频入库后，发布 MQ 消息
 * videoEventPublisher.publishVideoCreated(video, tagNames);
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布"视频已创建"事件
     *
     * @param video  刚入库的视频实体
     * @param tags   视频标签列表
     */
    public void publishVideoCreated(Video video, List<String> tags) {
        VideoProcessMessage message = new VideoProcessMessage(
                video.getId(),
                video.getUserId(),
                video.getTitle(),
                video.getVideoKey(),
                video.getDuration(),
                video.getSize(),
                tags,
                System.currentTimeMillis()
        );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_VIDEO,
                RabbitMqConfig.RK_VIDEO_CREATED,
                message
        );

        log.info("Published video.created event: videoId={}, routingKey={}",
                video.getId(), RabbitMqConfig.RK_VIDEO_CREATED);
    }
}
