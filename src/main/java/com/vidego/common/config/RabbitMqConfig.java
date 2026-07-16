package com.vidego.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 核心配置
 *
 * <p>设计用途：视频处理管道（Video Processing Pipeline）
 *
 * <h3>交换机与队列一览</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ [Topic] vidego.video.topic                                          │
 * │   ├─ routingKey: video.created  → vidego.video.cover.queue         │
 * │   ├─ routingKey: video.created  → vidego.video.audit.queue         │
 * │   └─ routingKey: video.created  → vidego.video.transcode.queue     │
 * │                                                                     │
 * │ [Topic] vidego.notification.topic                                   │
 * │   ├─ routingKey: notification.comment → vidego.notification.comment │
 * │   ├─ routingKey: notification.like    → vidego.notification.like   │
 * │   └─ routingKey: notification.follow  → vidego.notification.follow │
 * │                                                                     │
 * │ [Direct] vidego.view.direct                                         │
 * │   └─ routingKey: view.count     → vidego.view.count.queue          │
 * │                                                                     │
 * │ 死信转发（DLQ）：xxx.dlq → 重试 / 记录告警                         │
 * └─────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @see com.vidego.module.video.VideoServiceImpl#createVideo
 * @see com.vidego.module.comment.CommentServiceImpl#createComment
 * @see com.vidego.module.user.UserServiceImpl#follow
 */
@Slf4j
@Configuration
@EnableRabbit
public class RabbitMqConfig {

    // ========================================================================
    //  1. 交换机（Exchanges）
    // ========================================================================

    /** 视频生命周期事件交换机（Topic） */
    public static final String EXCHANGE_VIDEO = "vidego.video.topic";
    /** 通知事件交换机（Topic） */
    public static final String EXCHANGE_NOTIFICATION = "vidego.notification.topic";
    /** 播放量处理交换机（Direct） */
    public static final String EXCHANGE_VIEW = "vidego.view.direct";

    @Bean
    public TopicExchange videoEventExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_VIDEO)
                .durable(true)
                .build();
    }

    @Bean
    public TopicExchange notificationExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_NOTIFICATION)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange viewCountExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_VIEW)
                .durable(true)
                .build();
    }

    // ========================================================================
    //  2. 队列（Queues）— 视频处理管道
    // ========================================================================

    /** 队列：封面异步生成 */
    public static final String QUEUE_VIDEO_COVER = "vidego.video.cover.queue";
    /** 队列：内容审核（预留） */
    public static final String QUEUE_VIDEO_AUDIT = "vidego.video.audit.queue";
    /** 队列：视频转码（预留） */
    public static final String QUEUE_VIDEO_TRANSCODE = "vidego.video.transcode.queue";

    /** 队列：评论通知（预留） */
    public static final String QUEUE_NOTIFICATION_COMMENT = "vidego.notification.comment.queue";
    /** 队列：点赞通知（预留） */
    public static final String QUEUE_NOTIFICATION_LIKE = "vidego.notification.like.queue";
    /** 队列：关注通知（预留） */
    public static final String QUEUE_NOTIFICATION_FOLLOW = "vidego.notification.follow.queue";

    /** 队列：播放量批量写入（预留） */
    public static final String QUEUE_VIEW_COUNT = "vidego.view.count.queue";

    @Bean
    public Queue videoCoverQueue() {
        return QueueBuilder.durable(QUEUE_VIDEO_COVER)
                .deadLetterExchange(EXCHANGE_VIDEO)
                .deadLetterRoutingKey("video.cover.dlq")
                .build();
    }

    @Bean
    public Queue videoAuditQueue() {
        return QueueBuilder.durable(QUEUE_VIDEO_AUDIT)
                .build();
    }

    @Bean
    public Queue videoTranscodeQueue() {
        return QueueBuilder.durable(QUEUE_VIDEO_TRANSCODE)
                .build();
    }

    @Bean
    public Queue notificationCommentQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION_COMMENT)
                .build();
    }

    @Bean
    public Queue notificationLikeQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION_LIKE)
                .build();
    }

    @Bean
    public Queue notificationFollowQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION_FOLLOW)
                .build();
    }

    @Bean
    public Queue viewCountQueue() {
        return QueueBuilder.durable(QUEUE_VIEW_COUNT)
                .build();
    }

    // ========================================================================
    //  3. 绑定（Bindings）
    // ========================================================================

    /** 视频创建 routing key */
    public static final String RK_VIDEO_CREATED = "video.created";
    /** 评论通知 routing key */
    public static final String RK_NOTIFICATION_COMMENT = "notification.comment";
    /** 点赞通知 routing key */
    public static final String RK_NOTIFICATION_LIKE = "notification.like";
    /** 关注通知 routing key */
    public static final String RK_NOTIFICATION_FOLLOW = "notification.follow";
    /** 播放量 routing key */
    public static final String RK_VIEW_COUNT = "view.count";

    @Bean
    public Binding bindingVideoCover(
            @Qualifier("videoCoverQueue") Queue queue,
            TopicExchange videoEventExchange) {
        return BindingBuilder.bind(queue)
                .to(videoEventExchange)
                .with(RK_VIDEO_CREATED);
    }

    @Bean
    public Binding bindingVideoAudit(
            @Qualifier("videoAuditQueue") Queue queue,
            TopicExchange videoEventExchange) {
        return BindingBuilder.bind(queue)
                .to(videoEventExchange)
                .with(RK_VIDEO_CREATED);
    }

    @Bean
    public Binding bindingVideoTranscode(
            @Qualifier("videoTranscodeQueue") Queue queue,
            TopicExchange videoEventExchange) {
        return BindingBuilder.bind(queue)
                .to(videoEventExchange)
                .with(RK_VIDEO_CREATED);
    }

    @Bean
    public Binding bindingNotificationComment(
            @Qualifier("notificationCommentQueue") Queue queue,
            TopicExchange notificationExchange) {
        return BindingBuilder.bind(queue)
                .to(notificationExchange)
                .with(RK_NOTIFICATION_COMMENT);
    }

    @Bean
    public Binding bindingNotificationLike(
            @Qualifier("notificationLikeQueue") Queue queue,
            TopicExchange notificationExchange) {
        return BindingBuilder.bind(queue)
                .to(notificationExchange)
                .with(RK_NOTIFICATION_LIKE);
    }

    @Bean
    public Binding bindingNotificationFollow(
            @Qualifier("notificationFollowQueue") Queue queue,
            TopicExchange notificationExchange) {
        return BindingBuilder.bind(queue)
                .to(notificationExchange)
                .with(RK_NOTIFICATION_FOLLOW);
    }

    @Bean
    public Binding bindingViewCount(
            @Qualifier("viewCountQueue") Queue queue,
            DirectExchange viewCountExchange) {
        return BindingBuilder.bind(queue)
                .to(viewCountExchange)
                .with(RK_VIEW_COUNT);
    }

    // ========================================================================
    //  4. 消息序列化 & RabbitTemplate
    // ========================================================================

    /**
     * 使用 Jackson 将消息体自动序列化为 JSON。
     * 消费者侧无需手动转换，可直接接收 POJO。
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 自定义 RabbitTemplate，注入 JSON 序列化器。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // 确保 publisher 确认回调（需 yml 配置 publisher-confirm-type: correlated）
        template.setMandatory(true);
        return template;
    }
}
