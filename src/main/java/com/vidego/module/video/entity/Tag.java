package com.vidego.module.video.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("tag")
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField(fill = FieldFill.INSERT)
    private java.time.LocalDateTime createdAt;
}
