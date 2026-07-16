package com.vidego.module.video.mq;

import com.rabbitmq.client.Channel;
import com.vidego.common.config.RabbitMqConfig;
import com.vidego.module.video.CoverGenerationService;
import com.vidego.module.video.dto.VideoProcessMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 视频处理消费者 —— 异步执行创建视频后的"重量级"操作
 *
 * <p>监听 {@code vidego.video.cover.queue}，消费 {@link VideoProcessMessage}。</p>
 *
 * <h3>核心价值：异步解耦</h3>
 * <ul>
 *   <li>封面生成 → 从同步变异步，{@code createVideo} 接口秒回</li>
 *   <li>封面生成失败 → 消息进入死信队列，可重试 / 记录告警</li>
 *   <li>可横向扩展消费者实例，提升处理吞吐</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoCoverConsumer {

    private final CoverGenerationService coverGenerationService;

    /**
     * 消费视频创建事件，异步生成封面。
     *
     * <p>消息结构示例：</p>
     * <pre>
     * {
     *   "videoId": 42,
     *   "userId": 1001,
     *   "title": "我的旅行Vlog",
     *   "videoKey": "1001/abc-123.mp4",
     *   "duration": 120,
     *   "size": 52428800,
     *   "tags": ["旅行", "Vlog"],
     *   "timestamp": 1741910400000
     * }
     * </pre>
     */
    @RabbitListener(
            queues = RabbitMqConfig.QUEUE_VIDEO_COVER,
            concurrency = "3-5"  // 消费并发数：3~5 个线程
    )
    public void handleVideoCreated(VideoProcessMessage message, Channel channel, Message raw) {
        long deliveryTag = raw.getMessageProperties().getDeliveryTag();
        try {
            log.info("Received video process message: videoId={}, videoKey={}",
                    message.getVideoId(), message.getVideoKey());

            // ---------------------------------------------------------------
            //  TODO: 调用 FFmpeg 异步生成封面
            //  调用 videoService 的内部方法生成封面（需将现有 generateCoverWithUrl 抽出）
            // ---------------------------------------------------------------
            if (!coverGenerationService.generateCoverAsync(message.getVideoId(), message.getVideoKey(), message.getUserId())){
                throw new RuntimeException("Cover generation returned null");
            }
            // 处理成功 → 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("Cover generated successfully for videoId={}", message.getVideoId());

        } catch (Exception e) {
            log.error("Failed to process video message: videoId={}", message.getVideoId(), e);
            try {
                // 否定确认，requeue=false → 消息进入死信队列（DLQ）
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("Failed to nack message: videoId={}", message.getVideoId(), ex);
            }
        }
    }
}
