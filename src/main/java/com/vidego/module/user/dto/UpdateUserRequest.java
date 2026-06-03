package com.vidego.module.user.dto;

import lombok.Data;

@Data
public class UpdateUserRequest {

    private String nickname;
    private String bio;
    private String email;
    private String avatar;
}
