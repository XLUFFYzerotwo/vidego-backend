package com.vidego.module.notification.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知实体
 *
 * <p>用户收到通知后，可通过 REST API 分页查询历史通知，
 * 实时推送由消费者通过 WebSocket 完成。</p>
 *
 * @see com.vidego.module.notification.mq.NotificationConsumer
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收通知的用户 ID（谁收到通知） */
    @TableField("user_id")
    private Long userId;

    /** 触发通知的用户 ID（谁触发了通知） */
    @TableField("from_user_id")
    private Long fromUserId;

    /** 通知类型: comment / like / follow */
    private String type;

    /** 子类型: video_like / comment_like / comment_reply */
    @TableField("sub_type")
    private String subType;

    /** 关联的视频 ID（评论通知 / 视频点赞） */
    @TableField("video_id")
    private Long videoId;

    /** 关联的评论 ID（评论点赞 / 评论回复） */
    @TableField("comment_id")
    private Long commentId;

    /** 摘要文本（如评论前 50 字） */
    private String content;

    /** 是否已读: 0=未读 1=已读 */
    @TableField("is_read")
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
