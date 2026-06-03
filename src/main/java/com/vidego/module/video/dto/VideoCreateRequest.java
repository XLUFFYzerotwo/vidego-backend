package com.vidego.module.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class VideoCreateRequest {

    @NotBlank(message = "title is required")
    private String title;

    private String description;

    @NotBlank(message = "videoKey is required")
    private String videoKey;

    @NotNull(message = "duration is required")
    private Integer duration;

    @NotNull(message = "size is required")
    private Long size;

    private List<String> tags;
}
