package com.vidego.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "account is required")
    private String account;   // username or email

    @NotBlank(message = "password is required")
    private String password;
}
