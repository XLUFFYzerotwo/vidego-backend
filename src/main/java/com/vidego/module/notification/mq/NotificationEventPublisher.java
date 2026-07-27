package com.vidego.module.notification.mq;

import com.vidego.common.config.RabbitMqConfig;
import com.vidego.module.notification.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 通知事件发布器 —— 封装向 RabbitMQ 发送通知消息的逻辑
 *
 * <p>在业务 Service 的事务提交后调用此发布器，
 * 将评论、点赞、关注事件异步发送到通知队列进行持久化和实时推送。</p>
 *
 * <h3>集成方式</h3>
 * <pre>
 * // CommentServiceImpl.createComment() 事务提交后
 * notificationEventPublisher.publishCommentNotification(
 *     videoAuthorId, commenterId, videoId, commentId, content, isReply);
 *
 * // VideoServiceImpl.likeVideo() / CommentServiceImpl.likeComment() 事务提交后
 * notificationEventPublisher.publishLikeNotification(
 *     targetUserId, likerId, "video" / "comment", targetId, videoId);
 *
 * // UserServiceImpl.follow() 事务提交后
 * notificationEventPublisher.publishFollowNotification(targetUserId, followerId);
 * </pre>
 *
 * @see com.vidego.common.config.RabbitMqConfig#EXCHANGE_NOTIFICATION
 * @see com.vidego.module.notification.dto.NotificationMessage
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布评论通知
     *
     * <p>用户评论视频（根评论）时通知视频作者，
     * 用户回复评论时通知父评论作者。</p>
     *
     * @param toUserId       通知接收者（视频作者 / 父评论作者）
     * @param fromUserId     触发操作的用户（评论者）
     * @param videoId        关联视频 ID
     * @param commentId      评论 ID
     * @param contentSummary 评论内容摘要（截取前 50 字）
     * @param isReply        是否为回复（false=根评论, true=回复）
     */
    public void publishCommentNotification(
            Long toUserId, Long fromUserId, Long videoId,
            Long commentId, String contentSummary, boolean isReply) {
        if (toUserId.equals(fromUserId)) return;

        NotificationMessage msg = new NotificationMessage(
                "comment",
                isReply ? "comment_reply" : "comment_root",
                toUserId, fromUserId, videoId, commentId,
                truncateContent(contentSummary, 50),
                System.currentTimeMillis()
        );
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_NOTIFICATION,
                RabbitMqConfig.RK_NOTIFICATION_COMMENT,
                msg
        );

        log.info("Published comment notification: to={}, from={}, videoId={}, commentId={}, subType={}",
                toUserId, fromUserId, videoId, commentId, msg.getSubType());
    }

    /**
     * 发布点赞通知
     *
     * <p>用户点赞视频时通知视频作者，点赞评论时通知评论作者。</p>
     *
     * @param toUserId   通知接收者（视频作者 / 评论作者）
     * @param fromUserId 触发操作的用户（点赞者）
     * @param targetType 点赞目标类型："video" 或 "comment"
     * @param targetId   点赞目标 ID（videoId 或 commentId）
     * @param videoId    关联视频 ID（视频点赞时 = targetId，评论点赞时为评论所属视频 ID）
     */
    public void publishLikeNotification(
            Long toUserId, Long fromUserId, String targetType,
            Long targetId, Long videoId) {
        if (toUserId.equals(fromUserId)) return;

        String subType = "video".equals(targetType) ? "video_like" : "comment_like";
        NotificationMessage msg = new NotificationMessage(
                "like", subType, toUserId, fromUserId,
                videoId, "comment".equals(targetType) ? targetId : null,
                null, System.currentTimeMillis()
        );
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_NOTIFICATION,
                RabbitMqConfig.RK_NOTIFICATION_LIKE,
                msg
        );

        log.info("Published like notification: to={}, from={}, targetType={}, targetId={}",
                toUserId, fromUserId, targetType, targetId);
    }

    /**
     * 发布关注通知
     *
     * <p>用户关注另一个用户时，通知被关注者。</p>
     *
     * @param toUserId   通知接收者（被关注者）
     * @param fromUserId 触发操作的用户（关注者）
     */
    public void publishFollowNotification(Long toUserId, Long fromUserId) {
        if (toUserId.equals(fromUserId)) return;

        NotificationMessage msg = new NotificationMessage(
                "follow", "follow", toUserId, fromUserId,
                null, null, null, System.currentTimeMillis()
        );
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_NOTIFICATION,
                RabbitMqConfig.RK_NOTIFICATION_FOLLOW,
                msg
        );

        log.info("Published follow notification: to={}, from={}", toUserId, fromUserId);
    }

    /**
     * 截断内容到指定长度，超出部分丢弃。
     */
    private String truncateContent(String content, int maxLen) {
        if (content == null) return null;
        return content.length() <= maxLen ? content : content.substring(0, maxLen);
    }
}