package com.vidego.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "username is required")
    @Size(min = 2, max = 20, message = "username must be 2-20 characters")
    private String username;

    @NotBlank(message = "password is required")
    @Size(min = 6, max = 40, message = "password must be 6-40 characters")
    private String password;

    @NotBlank(message = "confirm password is required")
    private String confirmPassword;

    @Email(message = "invalid email format")
    private String email;
}
