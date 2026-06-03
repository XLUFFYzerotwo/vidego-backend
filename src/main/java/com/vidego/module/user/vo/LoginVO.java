package com.vidego.module.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {

    private String token;
    private String refreshToken;
    private UserVO user;
}
