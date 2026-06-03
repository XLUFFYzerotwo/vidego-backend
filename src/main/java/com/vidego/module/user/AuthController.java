package com.vidego.module.user;

import com.vidego.common.constant.AppConstant;
import com.vidego.common.result.Result;
import com.vidego.module.user.dto.ChangePasswordRequest;
import com.vidego.module.user.dto.LoginRequest;
import com.vidego.module.user.dto.RegisterRequest;
import com.vidego.module.user.vo.LoginVO;
import com.vidego.module.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "User registration, login, logout, and password management")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @Operation(summary = "Login with username/email and password")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @Operation(summary = "Refresh access token using refresh token")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody String refreshToken) {
        return Result.success(userService.refresh(refreshToken));
    }

    @Operation(summary = "Logout and invalidate current token")
    @PostMapping("/logout")
    public Result<Void> logout(
            @Parameter(hidden = true)
            @RequestHeader(AppConstant.TOKEN_HEADER) String authHeader) {
        String token = authHeader.replace(AppConstant.TOKEN_PREFIX, "").trim();
        userService.logout(token);
        return Result.success();
    }

    @Operation(summary = "Get current logged-in user info")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(userService.getCurrentUser());
    }

    @Operation(summary = "Change password (requires login)")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return Result.success();
    }
}
