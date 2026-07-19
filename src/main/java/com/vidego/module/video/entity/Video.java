package com.vidego.module.video.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video")
public class Video {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String title;

    private String description;

    @TableField("video_key")
    private String videoKey;

    @TableField("cover_key")
    private String coverKey;

    private Integer duration;

    private Long size;

    private Integer status;

    /**
     * 审核状态：0=待审核 1=审核通过 -1=审核不通过
     */
    @TableField("audit_status")
    private Integer auditStatus;

    /**
     * 审核不通过原因（audit_status = -1 时填充）
     */
    @TableField("audit_reason")
    private String auditReason;

    /**
     * 审核时间
     */
    @TableField("audited_at")
    private LocalDateTime auditedAt;

    /**
     * 审核人 ID（管理员 / 系统=0）
     */
    @TableField("auditor_id")
    private Long auditorId;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("comment_count")
    private Integer commentCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("hot_score")
    private Integer hotScore;
}
