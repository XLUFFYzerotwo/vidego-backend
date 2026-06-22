package com.vidego.module.danmaku;

import com.vidego.module.danmaku.dto.DanmakuCreateRequest;
import com.vidego.module.danmaku.dto.DanmakuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DanmakuWebSocketController {

    private final DanmakuService danmakuService;

    @MessageMapping("/danmaku/send")
    @SendTo("/topic/danmaku")
    public DanmakuVO handleDanmaku(DanmakuCreateRequest request,
                                   SimpMessageHeaderAccessor headerAccessor) {
        Long userId = null;
        // 优先从 Principal 获取（由 WebSocketConfig 的 interceptor 设置）
        if (headerAccessor.getUser() != null) {
            userId = Long.valueOf(headerAccessor.getUser().getName());
        }
        // 降级：从 session attributes 获取
        if (userId == null && headerAccessor.getSessionAttributes() != null) {
            userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        }
        if (userId == null) {
            throw new RuntimeException("未登录");
        }
        return danmakuService.createDanmaku(userId, request);
    }
}
