package com.vidego.auth;

import com.vidego.common.constant.AppConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * JWT 认证过滤器。
 * 提取 Authorization header 中的 token，若有效且未被 Redis 拉黑则设置 UserContext。
 * 不拦截任何请求 —— 是否登录由 Controller/Service 通过 UserContext.getUserId() 判断。
 */
@Slf4j
@Component
@Order(1)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenService tokenService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, TokenService tokenService) {
        this.jwtUtil = jwtUtil;
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AppConstant.TOKEN_HEADER);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(AppConstant.TOKEN_PREFIX)) {
            String token = authHeader.substring(AppConstant.TOKEN_PREFIX.length()).trim();
            if (jwtUtil.validateToken(token) && !tokenService.isBlacklisted(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                if (userId != null) {
                    UserContext.setUserId(userId);
                }
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
