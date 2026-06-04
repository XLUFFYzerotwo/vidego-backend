package com.vidego.module.user;

import com.vidego.common.constant.AppConstant;
import com.vidego.common.exception.BusinessException;
import com.vidego.common.result.ErrorCode;
import com.vidego.common.result.Result;
import com.vidego.module.user.dto.ChangePasswordRequest;
import com.vidego.module.user.dto.LoginRequest;
import com.vidego.module.user.dto.RegisterRequest;
import com.vidego.module.user.vo.LoginVO;
import com.vidego.module.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@Tag(name = "Authentication", description = "User registration, login, logout, and password management")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final StringRedisTemplate redisTemplate;

    private static final long LOGIN_LOCK_TTL_SECONDS = 300; // 5 分钟

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @Operation(summary = "Login with username/email and password")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request,
                                 @Parameter(hidden = true) HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String failKey = "vidego:login:fail:" + ip;

        // 检查是否已被锁定
        String failStr = redisTemplate.opsForValue().get(failKey);
        if (failStr != null) {
            int fails = Integer.parseInt(failStr);
            if (fails >= 5) {
                return Result.error(429, "too many attempts, please try later");
            }
        }

        try {
            LoginVO loginVO = userService.login(request);
            // 登录成功，清除失败计数
            redisTemplate.delete(failKey);
            return Result.success(loginVO);
        } catch (BusinessException e) {
            // 登录失败，递增计数
            redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, LOGIN_LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            throw e;
        }
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

    /**
     * 获取客户端真实 IP（支持反向代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
