package com.vidego.module.video.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 视频处理消息体 —— RabbitMQ 消息负载
 *
 * <p>当用户创建视频后，{@link com.vidego.module.video.VideoServiceImpl#createVideo}
 * 向交换机 {@code vidego.video.topic} 发布此消息，
 * 各消费者（封面生成 / 转码 / 审核）异步处理。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoProcessMessage {

    /** 视频 ID（数据库主键） */
    private Long videoId;

    /** 上传用户 ID */
    private Long userId;

    /** 视频标题 */
    private String title;

    /** 视频描述 */
    private String description;

    /** MinIO 中的视频对象 key */
    private String videoKey;

    /** 视频时长（秒） */
    private Integer duration;

    /** 视频文件大小（字节） */
    private Long size;

    /** 标签列表 */
    private List<String> tags;

    /** 事件时间戳（毫秒） */
    private Long timestamp;
}
