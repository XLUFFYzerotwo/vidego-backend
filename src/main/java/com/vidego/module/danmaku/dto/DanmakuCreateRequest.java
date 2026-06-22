package com.vidego.module.danmaku.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DanmakuCreateRequest {
    @NotNull(message = "视频ID不能为空")
    private Long videoId;

    @NotBlank(message = "内容不能为空")
    @Size(max = 100, message = "弹幕内容不能超过100字")
    private String content;

    @NotNull(message = "时间点不能为空")
    private Float time;

    private String color = "#FFFFFF";

    private Integer type = 0;
}
