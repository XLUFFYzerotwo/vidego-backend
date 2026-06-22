package com.vidego.module.danmaku.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.vidego.module.user.vo.UserVO;
import lombok.Data;

@Data
public class DanmakuVO {

    private Long id;
    private Long videoId;
    private Long userId;
    private String content;
    private Float time;
    private String color;
    private Integer type;
    private String createdAt;
    private UserVO user;

}
