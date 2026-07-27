package com.vidego.module.notification.dto;

import com.vidego.module.user.vo.UserVO;
import lombok.Data;

/**
 * 通知展示 VO —— 前端 REST API / WebSocket 推送用
 *
 * <p>相比 {@link NotificationMessage}（MQ 消息体），
 * 此 VO 增加了已读状态、格式化的时间、触发用户信息等前端展示需要的字段。</p>
 */
@Data
public class NotificationVO {

    private Long id;

    /** 通知类型: comment / like / follow */
    private String type;

    /** 子类型: comment_root / comment_reply / video_like / comment_like / follow */
    private String subType;

    /** 关联视频 ID */
    private Long videoId;

    /** 关联评论 ID */
    private Long commentId;

    /** 摘要文本 */
    private String content;

    /** 是否已读: 0=未读 1=已读 */
    private Integer isRead;

    /** 创建时间（格式化字符串） */
    private String createdAt;

    /** 触发操作的用户信息 */
    private UserVO fromUser;
}
