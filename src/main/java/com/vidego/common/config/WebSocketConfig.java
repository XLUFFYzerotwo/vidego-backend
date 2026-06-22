package com.vidego.common.config;

import com.vidego.auth.JwtUtil;
import com.vidego.auth.TokenService;
import com.vidego.common.constant.AppConstant;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;
    private final TokenService tokenService;

    public WebSocketConfig(JwtUtil jwtUtil, TokenService tokenService) {
        this.jwtUtil = jwtUtil;
        this.tokenService = tokenService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/ws/danmaku")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (StringUtils.hasText(authHeader) && authHeader.startsWith(AppConstant.TOKEN_PREFIX)) {
                        String token = authHeader.substring(AppConstant.TOKEN_PREFIX.length()).trim();
                        if (jwtUtil.validateToken(token) && !tokenService.isBlacklisted(token)) {
                            Long userId = jwtUtil.getUserIdFromToken(token);
                            if (userId != null) {
                                // 使用 setUser 设置 Principal（Spring 自动传播到后续消息）
                                Long finalUserId = userId;
                                accessor.setUser(() -> String.valueOf(finalUserId));
                                // 保留 session attributes 作为备用
                                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                                if (sessionAttributes == null) {
                                    sessionAttributes = new HashMap<>();
                                    accessor.setSessionAttributes(sessionAttributes);
                                }
                                sessionAttributes.put("userId", userId);
                            }
                        }
                    }
                }
                return message;
            }
        });
    }
}
