package com.vidego.module.admin;

import com.rabbitmq.client.Channel;
import com.vidego.common.config.RabbitMqConfig;
import com.vidego.module.admin.TextAuditService.AuditResult;
import com.vidego.module.video.VideoService;
import com.vidego.module.video.dto.VideoProcessMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 视频自动审核消费者
 *
 * <p>监听 {@code vidego.video.audit.queue}，在视频创建后异步执行文字审核。</p>
 *
 * <h3>审核策略</h3>
 * <ul>
 *   <li>文字违规（标题 / 描述 / 标签）→ 直接驳回（audit_status = -1）</li>
 *   <li>文字通过 → 保持 audit_status = 0（待审核），由管理员在后台人工复核</li>
 *   <li>视频画面审核（Phase 2）：对接第三方 API，异步回调更新状态</li>
 * </ul>
 *
 * <p>所有审核结果通过 {@link VideoService#updateAuditStatus} 写回 video 表，
 * auditorId = 0 表示系统自动审核。人工审核由 {@link VideoAuditController} 接管。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminVideoAuditConsumer {

    /** 系统自动审核使用的审核人 ID（0 = 系统） */
    private static final long SYSTEM_AUDITOR_ID = 0L;

    private final TextAuditService textAuditService;
    private final VideoService videoService;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_VIDEO_AUDIT)
    public void handleVideoAudit(VideoProcessMessage message, Channel channel, Message raw) {
        long deliveryTag = raw.getMessageProperties().getDeliveryTag();
        if (message == null || message.getVideoId() == null) {
            log.warn("Audit message is invalid, discarding: {}", message);
            ackQuietly(channel, deliveryTag, false);
            return;
        }
        log.info("Received video audit message: videoId={}", message.getVideoId());

        try {
            // 1. 文字审核（标题 + 描述 + 标签）
            AuditResult textResult = textAuditService.audit(
                    message.getTitle(),
                    message.getDescription(),
                    message.getTags() != null ? String.join(",", message.getTags()) : null);

            if (textResult.isRejected()) {
                // 文字违规 → 直接驳回（audit_status = -1）
                videoService.updateAuditStatus(message.getVideoId(), -1, textResult.getReason(), SYSTEM_AUDITOR_ID);
                log.info("Video rejected by text audit: videoId={}, reason={}",
                        message.getVideoId(), textResult.getReason());
                ackQuietly(channel, deliveryTag, false);
                return;
            }

            // 2. 文字审核通过：保持 audit_status = 0（待审核），由管理员在后台人工复核
            //    视频画面审核（第三方 API 异步回调）结果后续会回调更新状态
            //    暂不自动通过，避免敏感词绕过 + 违规视频直接上线
            log.info("Text audit passed, waiting for admin manual review: videoId={}", message.getVideoId());

            ackQuietly(channel, deliveryTag, false);
        } catch (Exception e) {
            log.error("Video audit failed, requeue for retry: videoId={}", message.getVideoId(), e);
            // requeue=true 让 MQ 重试；持续失败需配合死信队列兜底
            nackQuietly(channel, deliveryTag, true);
        }
    }

    private void ackQuietly(Channel channel, long deliveryTag, boolean multiple) {
        try {
            channel.basicAck(deliveryTag, multiple);
        } catch (IOException e) {
            log.error("Failed to ack audit message: deliveryTag={}", deliveryTag, e);
        }
    }

    private void nackQuietly(Channel channel, long deliveryTag, boolean requeue) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (IOException e) {
            log.error("Failed to nack audit message: deliveryTag={}", deliveryTag, e);
        }
    }
}
