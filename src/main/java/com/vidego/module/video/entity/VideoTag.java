package com.vidego.module.video.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("video_tag")
public class VideoTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("video_id")
    private Long videoId;

    @TableField("tag_id")
    private Long tagId;
}
