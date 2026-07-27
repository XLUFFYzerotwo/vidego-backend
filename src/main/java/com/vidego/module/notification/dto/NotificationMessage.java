package com.vidego.module.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知消息体 —— RabbitMQ 消息负载
 *
 * <p>当用户触发评论、点赞、关注等操作后，
 * {@code NotificationEventPublisher} 向交换机
 * {@code vidego.notification.topic} 发布此消息，
 * 消费者异步持久化并推送 WebSocket。</p>
 *
 * <h3>消息示例</h3>
 * <pre>
 * // 根评论通知（B 评论了 A 的视频）
 * { "type": "comment", "subType": "comment_root",
 *   "toUserId": 1001, "fromUserId": 1002, "videoId": 42,
 *   "commentId": 301,
 *   "summary": "这个视频太棒了！请问背景音乐是什么？",
 *   "timestamp": 1741910400000 }
 *
 * // 回复通知（C 回复了 B 的评论）
 * { "type": "comment", "subType": "comment_reply",
 *   "toUserId": 1002, "fromUserId": 1003, "videoId": 42,
 *   "commentId": 305,
 *   "summary": "@userB 我觉得你说的对",
 *   "timestamp": 1741910500000 }
 *
 * // 视频点赞通知
 * { "type": "like", "subType": "video_like",
 *   "toUserId": 1001, "fromUserId": 1002, "videoId": 42,
 *   "timestamp": 1741910600000 }
 *
 * // 评论点赞通知
 * { "type": "like", "subType": "comment_like",
 *   "toUserId": 1002, "fromUserId": 1003,
 *   "videoId": 42, "commentId": 88,
 *   "timestamp": 1741910650000 }
 *
 * // 关注通知
 * { "type": "follow", "subType": "follow",
 *   "toUserId": 1001, "fromUserId": 1002,
 *   "timestamp": 1741910700000 }
 * </pre>
 *
 * @see com.vidego.common.config.RabbitMqConfig#EXCHANGE_NOTIFICATION
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    /** 通知类型: comment / like / follow */
    private String type;

    /** 子类型: comment_root / comment_reply / video_like / comment_like / follow */
    private String subType;

    /** 接收通知的用户 ID */
    private Long toUserId;

    /** 触发操作的用户 ID */
    private Long fromUserId;

    /** 关联视频 ID（评论通知 / 视频点赞时有值） */
    private Long videoId;

    /** 关联评论 ID（评论点赞 / 评论回复时有值） */
    private Long commentId;

    /** 摘要内容（评论通知时截取前 50 字） */
    private String summary;

    /** 事件时间戳（毫秒） */
    private Long timestamp;
}
