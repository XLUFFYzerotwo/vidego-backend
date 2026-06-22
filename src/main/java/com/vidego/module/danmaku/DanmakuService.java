package com.vidego.module.danmaku;

import com.vidego.module.danmaku.dto.DanmakuCreateRequest;
import com.vidego.module.danmaku.dto.DanmakuVO;

import java.util.List;

public interface DanmakuService {
    DanmakuVO createDanmaku(Long userId, DanmakuCreateRequest request);
    List<DanmakuVO> getDanmakuByVideoId(Long videoId, Float startTime, Float endTime);
    boolean canSend(Long userId);
}
