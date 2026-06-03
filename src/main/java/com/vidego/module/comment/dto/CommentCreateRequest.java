package com.vidego.module.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentCreateRequest {

    @NotBlank(message = "comment content is required")
    @Size(max = 500, message = "comment must not exceed 500 characters")
    private String content;

    /** null 表示根评论，非 null 表示回复某条评论 */
    private Long parentId;
}
