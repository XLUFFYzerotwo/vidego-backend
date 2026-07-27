package com.vidego.module.notification.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.vidego.common.config.RabbitMqConfig;
import com.vidego.module.notification.dto.NotificationMessage;
import com.vidego.module.notification.dto.NotificationVO;
import com.vidego.module.notification.entity.Notification;
import com.vidego.module.notification.mapper.NotificationMapper;
import com.vidego.module.user.entity.User;
import com.vidego.module.user.mapper.UserMapper;
import com.vidego.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 通知消费者 —— 异步消费通知消息并完成持久化 + WebSocket 实时推送
 *
 * <p>监听三个通知队列，统一调用 {@link #processAndPush}：
 * <ul>
 *   <li>{@code vidego.notification.comment.queue} — 评论通知</li>
 *   <li>{@code vidego.notification.like.queue}    — 点赞通知</li>
 *   <li>{@code vidego.notification.follow.queue}  — 关注通知</li>
 * </ul>
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>反序列化 {@link NotificationMessage}</li>
 *   <li>写入 {@code notification} 表持久化</li>
 *   <li>通过 {@link SimpMessagingTemplate} 推送到
 *       {@code /topic/notifications/{userId}}（单条通知）和
 *       {@code /topic/notifications/{userId}/unread}（未读计数）</li>
 *   <li>手动 ACK — 成功确认，失败进入死信队列（DLQ）</li>
 * </ol>
 *
 * <h3>容错</h3>
 * <p>消费失败时执行 {@code basicNack(requeue=false)}，
 * 消息进入对应 DLQ（需在 {@link com.vidego.common.config.RabbitMqConfig}
 * 中为通知队列配置死信绑定）。</p>
 *
 * @see com.vidego.common.config.RabbitMqConfig#QUEUE_NOTIFICATION_COMMENT
 * @see com.vidego.common.config.RabbitMqConfig#QUEUE_NOTIFICATION_LIKE
 * @see com.vidego.common.config.RabbitMqConfig#QUEUE_NOTIFICATION_FOLLOW
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;  // WebSocket 推送

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    @RabbitListener(queues = RabbitMqConfig.QUEUE_NOTIFICATION_COMMENT)
    public void handleCommentNotification(
          NotificationMessage msg, Channel channel, Message raw) {
      processAndPush(msg, channel, raw);
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_NOTIFICATION_LIKE)
    public void handleLikeNotification(
          NotificationMessage msg, Channel channel, Message raw) {
      processAndPush(msg, channel, raw);
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_NOTIFICATION_FOLLOW)
    public void handleFollowNotification(
          NotificationMessage msg, Channel channel, Message raw) {
      processAndPush(msg, channel, raw);
    }

    private void processAndPush(
          NotificationMessage msg, Channel channel, Message raw) {
      long deliveryTag = raw.getMessageProperties().getDeliveryTag();
      try {
          // 1. 幂等守卫：DLQ 重试时避免重复插入
          if (isDuplicate(msg)) {
              channel.basicAck(deliveryTag, false);
              log.debug("Duplicate notification ignored: type={}, to={}, from={}",
                      msg.getType(), msg.getToUserId(), msg.getFromUserId());
              return;
          }

          // 2. 硬依赖：持久化到 DB（失败 → Nack → DLQ）
          Notification notification = toEntity(msg);
          notificationMapper.insert(notification);

          // DB 写入成功后立即 ACK，后续 WebSocket 失败不影响消息状态
          channel.basicAck(deliveryTag, false);
          log.debug("Notification persisted: type={}, id={}, to={}, from={}",
                  msg.getType(), notification.getId(), msg.getToUserId(), msg.getFromUserId());

          // 3. 软依赖：WebSocket 实时推送（失败仅记日志，不影响已 ACK 的消息）
          try {
              NotificationVO vo = toVO(notification);
              messagingTemplate.convertAndSend(
                      "/topic/notifications/" + msg.getToUserId(), vo);

              Long unreadCount = notificationMapper.selectCount(
                      new LambdaQueryWrapper<Notification>()
                              .eq(Notification::getUserId, msg.getToUserId())
                              .eq(Notification::getIsRead, 0));
              messagingTemplate.convertAndSend(
                      "/topic/notifications/" + msg.getToUserId() + "/unread",
                      Map.of("count", unreadCount));
          } catch (Exception wsEx) {
              log.warn("WebSocket push failed (notification already persisted): to={}, type={}",
                      msg.getToUserId(), msg.getType(), wsEx);
          }

      } catch (Exception e) {
          log.error("Failed to persist notification: to={}, type={}",
                  msg.getToUserId(), msg.getType(), e);
          try {
              channel.basicNack(deliveryTag, false, false);
          } catch (IOException ex) {
              log.error("Failed to nack", ex);
          }
      }
    }

    /**
     * 幂等检查：基于 (userId, fromUserId, type) + 目标 ID 防重。
     *
     * <p>comment 通知按 commentId 去重（同一评论同一人只通知一次），
     * like 通知按 subType + 目标 ID 去重（区分 video_like / comment_like），
     * follow 通知按 (userId, fromUserId, type) 去重（每个关注关系只通知一次）。</p>
     */
    private boolean isDuplicate(NotificationMessage msg) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, msg.getToUserId())
                .eq(Notification::getFromUserId, msg.getFromUserId())
                .eq(Notification::getType, msg.getType());

        if ("comment".equals(msg.getType()) && msg.getCommentId() != null) {
            wrapper.eq(Notification::getCommentId, msg.getCommentId());
        } else if ("like".equals(msg.getType())) {
            wrapper.eq(Notification::getSubType, msg.getSubType());
            if (msg.getCommentId() != null) {
                wrapper.eq(Notification::getCommentId, msg.getCommentId());
            } else {
                wrapper.eq(Notification::getVideoId, msg.getVideoId());
            }
        }
        // follow: userId + fromUserId + type 已覆盖

        return notificationMapper.selectCount(wrapper) > 0;
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO notificationVO = new NotificationVO();
        User user = userMapper.selectById(notification.getFromUserId());
        notificationVO.setId(notification.getId());
        notificationVO.setType(notification.getType());
        notificationVO.setSubType(notification.getSubType());
        notificationVO.setVideoId(notification.getVideoId());
        notificationVO.setCommentId(notification.getCommentId());
        notificationVO.setContent(notification.getContent());
        notificationVO.setIsRead(notification.getIsRead());
        notificationVO.setCreatedAt(notification.getCreatedAt() != null
                ? notification.getCreatedAt().format(DTF) : null);
        notificationVO.setFromUser(toUserVO(user));
        return notificationVO;
    }

    private Notification toEntity(NotificationMessage msg) {
        Notification notification = new Notification();
        notification.setUserId(msg.getToUserId());
        notification.setFromUserId(msg.getFromUserId());
        notification.setType(msg.getType());
        notification.setSubType(msg.getSubType());
        notification.setVideoId(msg.getVideoId());
        notification.setCommentId(msg.getCommentId());
        notification.setContent(msg.getSummary());
        notification.setIsRead(0);
        return notification;
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
}