package com.vidego.module.video.dto;

import com.vidego.module.user.vo.UserVO;
import lombok.Data;

import java.util.List;

@Data
public class VideoVO {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String videoUrl;
    private String coverUrl;
    private Integer duration;
    private Long size;
    private Integer status;
    /**
     * 审核状态：0=待审核 1=通过 -1=不通过
     */
    private Integer auditStatus;
    /**
     * 审核不通过原因
     */
    private String auditReason;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private List<String> tags;
    private UserVO user;
    private String createdAt;
}
