package com.vidego.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "old password is required")
    private String oldPassword;

    @NotBlank(message = "new password is required")
    @Size(min = 6, max = 40, message = "password must be 6-40 characters")
    private String newPassword;

    @NotBlank(message = "confirm password is required")
    private String confirmNewPassword;
}
