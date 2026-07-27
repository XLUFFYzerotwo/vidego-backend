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
 * RabbitMQ 核心配置 — 统一的事件总线 + 死信架构
 *
 * <h3>拓扑一览</h3>
 * <pre>
 * ═══════════════════════════════════════════════════════════════════════
 *  业务交换机与主队列绑定
 * ═══════════════════════════════════════════════════════════════════════
 * [Topic] vidego.video.topic
 *   ├─ video.created  → vidego.video.cover.queue     (封面生成)
 *   ├─ video.created  → vidego.video.audit.queue     (内容审核)
 *   └─ video.created  → vidego.video.transcode.queue (转码, 预留)
 *
 * [Topic] vidego.notification.topic
 *   ├─ notification.comment  → vidego.notification.comment.queue
 *   ├─ notification.like     → vidego.notification.like.queue
 *   └─ notification.follow   → vidego.notification.follow.queue
 *
 * [Direct] vidego.view.direct
 *   └─ view.count  → vidego.view.count.queue
 *
 * ═══════════════════════════════════════════════════════════════════════
 *  死信架构（每个业务域独立 DLX + DLQ）
 * ═══════════════════════════════════════════════════════════════════════
 * [Direct] vidego.video.dlx
 *   ├─ video.cover.dlq     → vidego.video.cover.dlq
 *   ├─ video.audit.dlq     → vidego.video.audit.dlq
 *   └─ video.transcode.dlq → vidego.video.transcode.dlq
 *
 * [Direct] vidego.notification.dlx
 *   ├─ dlq.comment  → vidego.notification.comment.dlq
 *   ├─ dlq.like     → vidego.notification.like.dlq
 *   └─ dlq.follow   → vidego.notification.follow.dlq
 * </pre>
 *
 * <h3>消费策略（硬/软依赖分离）</h3>
 * <table>
 *   <tr><th>域</th><th>硬依赖（失败→DLQ）</th><th>软依赖（失败→仅日志）</th></tr>
 *   <tr><td>视频</td><td>封⾯生成 / 审核</td><td>—</td></tr>
 *   <tr><td>通知</td><td>DB 持久化</td><td>WebSocket 推送</td></tr>
 * </table>
 *
 * @see com.vidego.module.video.VideoServiceImpl#createVideo
 * @see com.vidego.module.comment.CommentServiceImpl#createComment
 * @see com.vidego.module.user.UserServiceImpl#follow
 */
@Slf4j
@Configuration
@EnableRabbit
public class RabbitMqConfig {

    // ═══════════════════════════════════════════════════════════════════
    //  1. 交换机常量 + Bean
    // ═══════════════════════════════════════════════════════════════════

    // ── 业务交换机 ──

    public static final String EXCHANGE_VIDEO         = "vidego.video.topic";
    public static final String EXCHANGE_NOTIFICATION  = "vidego.notification.topic";
    public static final String EXCHANGE_VIEW          = "vidego.view.direct";

    // ── 死信交换机 ──

    public static final String EXCHANGE_VIDEO_DLX         = "vidego.video.dlx";
    public static final String EXCHANGE_NOTIFICATION_DLX  = "vidego.notification.dlx";

    @Bean public TopicExchange videoEventExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_VIDEO).durable(true).build();
    }
    @Bean public TopicExchange notificationExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_NOTIFICATION).durable(true).build();
    }
    @Bean public DirectExchange viewCountExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_VIEW).durable(true).build();
    }
    @Bean public DirectExchange videoDlxExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_VIDEO_DLX).durable(true).build();
    }
    @Bean public DirectExchange notificationDlxExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_NOTIFICATION_DLX).durable(true).build();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  2. 队列常量 — 主队列
    // ═══════════════════════════════════════════════════════════════════

    // ── 视频域 ──

    public static final String QUEUE_VIDEO_COVER     = "vidego.video.cover.queue";
    public static final String QUEUE_VIDEO_AUDIT     = "vidego.video.audit.queue";
    public static final String QUEUE_VIDEO_TRANSCODE = "vidego.video.transcode.queue";

    // ── 通知域 ──

    public static final String QUEUE_NOTIFICATION_COMMENT = "vidego.notification.comment.queue";
    public static final String QUEUE_NOTIFICATION_LIKE    = "vidego.notification.like.queue";
    public static final String QUEUE_NOTIFICATION_FOLLOW  = "vidego.notification.follow.queue";

    // ── 其他 ──

    public static final String QUEUE_VIEW_COUNT = "vidego.view.count.queue";

    // ═══════════════════════════════════════════════════════════════════
    //  2b. 队列常量 — 死信队列（DLQ）
    // ═══════════════════════════════════════════════════════════════════

    // ── 视频 DLQ ──

    public static final String DLQ_VIDEO_COVER     = "vidego.video.cover.dlq";
    public static final String DLQ_VIDEO_AUDIT     = "vidego.video.audit.dlq";
    public static final String DLQ_VIDEO_TRANSCODE = "vidego.video.transcode.dlq";

    // ── 通知 DLQ ──

    public static final String DLQ_NOTIFICATION_COMMENT = "vidego.notification.comment.dlq";
    public static final String DLQ_NOTIFICATION_LIKE    = "vidego.notification.like.dlq";
    public static final String DLQ_NOTIFICATION_FOLLOW  = "vidego.notification.follow.dlq";

    // ═══════════════════════════════════════════════════════════════════
    //  3. 主队列 Bean（每个队列绑定到对应 DLX）
    // ═══════════════════════════════════════════════════════════════════

    // ── 视频主队列 ──

    @Bean public Queue videoCoverQueue() {
        return QueueBuilder.durable(QUEUE_VIDEO_COVER)
                .deadLetterExchange(EXCHANGE_VIDEO_DLX)
                .deadLetterRoutingKey(RK_DLQ_VIDEO_COVER)
                .build();
    }
    @Bean public Queue videoAuditQueue() {
        return QueueBuilder.durable(QUEUE_VIDEO_AUDIT)
                .deadLetterExchange(EXCHANGE_VIDEO_DLX)
                .deadLetterRoutingKey(RK_DLQ_VIDEO_AUDIT)
                .build();
    }
    @Bean public Queue videoTranscodeQueue() {
        return QueueBuilder.durable(QUEUE_VIDEO_TRANSCODE)
                .deadLetterExchange(EXCHANGE_VIDEO_DLX)
                .deadLetterRoutingKey(RK_DLQ_VIDEO_TRANSCODE)
                .build();
    }

    // ── 通知主队列 ──

    @Bean public Queue notificationCommentQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION_COMMENT)
                .deadLetterExchange(EXCHANGE_NOTIFICATION_DLX)
                .deadLetterRoutingKey(RK_DLQ_NOTIFICATION_COMMENT)
                .build();
    }
    @Bean public Queue notificationLikeQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION_LIKE)
                .deadLetterExchange(EXCHANGE_NOTIFICATION_DLX)
                .deadLetterRoutingKey(RK_DLQ_NOTIFICATION_LIKE)
                .build();
    }
    @Bean public Queue notificationFollowQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION_FOLLOW)
                .deadLetterExchange(EXCHANGE_NOTIFICATION_DLX)
                .deadLetterRoutingKey(RK_DLQ_NOTIFICATION_FOLLOW)
                .build();
    }

    // ── 其他主队列 ──

    @Bean public Queue viewCountQueue() {
        return QueueBuilder.durable(QUEUE_VIEW_COUNT).build();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  3b. 死信队列 Bean（DLQ — 故障消息暂存）
    // ═══════════════════════════════════════════════════════════════════

    // ── 视频 DLQ ──

    @Bean public Queue videoCoverDlq() {
        return QueueBuilder.durable(DLQ_VIDEO_COVER).build();
    }
    @Bean public Queue videoAuditDlq() {
        return QueueBuilder.durable(DLQ_VIDEO_AUDIT).build();
    }
    @Bean public Queue videoTranscodeDlq() {
        return QueueBuilder.durable(DLQ_VIDEO_TRANSCODE).build();
    }

    // ── 通知 DLQ ──

    @Bean public Queue notificationCommentDlq() {
        return QueueBuilder.durable(DLQ_NOTIFICATION_COMMENT).build();
    }
    @Bean public Queue notificationLikeDlq() {
        return QueueBuilder.durable(DLQ_NOTIFICATION_LIKE).build();
    }
    @Bean public Queue notificationFollowDlq() {
        return QueueBuilder.durable(DLQ_NOTIFICATION_FOLLOW).build();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  4. Routing Key 常量
    // ═══════════════════════════════════════════════════════════════════

    // ── 业务 routing keys ──

    public static final String RK_VIDEO_CREATED        = "video.created";
    public static final String RK_NOTIFICATION_COMMENT = "notification.comment";
    public static final String RK_NOTIFICATION_LIKE    = "notification.like";
    public static final String RK_NOTIFICATION_FOLLOW  = "notification.follow";
    public static final String RK_VIEW_COUNT           = "view.count";

    // ── 死信 routing keys ──

    public static final String RK_DLQ_VIDEO_COVER     = "video.cover.dlq";
    public static final String RK_DLQ_VIDEO_AUDIT     = "video.audit.dlq";
    public static final String RK_DLQ_VIDEO_TRANSCODE = "video.transcode.dlq";

    public static final String RK_DLQ_NOTIFICATION_COMMENT = "dlq.comment";
    public static final String RK_DLQ_NOTIFICATION_LIKE    = "dlq.like";
    public static final String RK_DLQ_NOTIFICATION_FOLLOW  = "dlq.follow";

    // ═══════════════════════════════════════════════════════════════════
    //  5. 绑定 — 业务交换机 → 主队列
    // ═══════════════════════════════════════════════════════════════════

    // ── 视频域 ──

    @Bean public Binding bindingVideoCover(
            @Qualifier("videoCoverQueue") Queue q, TopicExchange videoEventExchange) {
        return BindingBuilder.bind(q).to(videoEventExchange).with(RK_VIDEO_CREATED);
    }
    @Bean public Binding bindingVideoAudit(
            @Qualifier("videoAuditQueue") Queue q, TopicExchange videoEventExchange) {
        return BindingBuilder.bind(q).to(videoEventExchange).with(RK_VIDEO_CREATED);
    }
    @Bean public Binding bindingVideoTranscode(
            @Qualifier("videoTranscodeQueue") Queue q, TopicExchange videoEventExchange) {
        return BindingBuilder.bind(q).to(videoEventExchange).with(RK_VIDEO_CREATED);
    }

    // ── 通知域 ──

    @Bean public Binding bindingNotificationComment(
            @Qualifier("notificationCommentQueue") Queue q, TopicExchange notificationExchange) {
        return BindingBuilder.bind(q).to(notificationExchange).with(RK_NOTIFICATION_COMMENT);
    }
    @Bean public Binding bindingNotificationLike(
            @Qualifier("notificationLikeQueue") Queue q, TopicExchange notificationExchange) {
        return BindingBuilder.bind(q).to(notificationExchange).with(RK_NOTIFICATION_LIKE);
    }
    @Bean public Binding bindingNotificationFollow(
            @Qualifier("notificationFollowQueue") Queue q, TopicExchange notificationExchange) {
        return BindingBuilder.bind(q).to(notificationExchange).with(RK_NOTIFICATION_FOLLOW);
    }

    // ── 其他 ──

    @Bean public Binding bindingViewCount(
            @Qualifier("viewCountQueue") Queue q, DirectExchange viewCountExchange) {
        return BindingBuilder.bind(q).to(viewCountExchange).with(RK_VIEW_COUNT);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  5b. 绑定 — 死信交换机 → DLQ 队列
    // ═══════════════════════════════════════════════════════════════════

    // ── 视频 DLQ 绑定 ──

    @Bean public Binding bindingDlqVideoCover(
            @Qualifier("videoCoverDlq") Queue q, DirectExchange videoDlxExchange) {
        return BindingBuilder.bind(q).to(videoDlxExchange).with(RK_DLQ_VIDEO_COVER);
    }
    @Bean public Binding bindingDlqVideoAudit(
            @Qualifier("videoAuditDlq") Queue q, DirectExchange videoDlxExchange) {
        return BindingBuilder.bind(q).to(videoDlxExchange).with(RK_DLQ_VIDEO_AUDIT);
    }
    @Bean public Binding bindingDlqVideoTranscode(
            @Qualifier("videoTranscodeDlq") Queue q, DirectExchange videoDlxExchange) {
        return BindingBuilder.bind(q).to(videoDlxExchange).with(RK_DLQ_VIDEO_TRANSCODE);
    }

    // ── 通知 DLQ 绑定 ──

    @Bean public Binding bindingDlqNotificationComment(
            @Qualifier("notificationCommentDlq") Queue q, DirectExchange notificationDlxExchange) {
        return BindingBuilder.bind(q).to(notificationDlxExchange).with(RK_DLQ_NOTIFICATION_COMMENT);
    }
    @Bean public Binding bindingDlqNotificationLike(
            @Qualifier("notificationLikeDlq") Queue q, DirectExchange notificationDlxExchange) {
        return BindingBuilder.bind(q).to(notificationDlxExchange).with(RK_DLQ_NOTIFICATION_LIKE);
    }
    @Bean public Binding bindingDlqNotificationFollow(
            @Qualifier("notificationFollowDlq") Queue q, DirectExchange notificationDlxExchange) {
        return BindingBuilder.bind(q).to(notificationDlxExchange).with(RK_DLQ_NOTIFICATION_FOLLOW);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  6. 消息序列化 & RabbitTemplate
    // ═══════════════════════════════════════════════════════════════════

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
        template.setMandatory(true); // publisher confirm 回调
        return template;
    }
}
