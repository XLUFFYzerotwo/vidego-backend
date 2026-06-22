package com.vidego.module.danmaku;

import com.vidego.common.exception.BusinessException;
import com.vidego.common.result.ErrorCode;
import com.vidego.common.result.Result;
import com.vidego.module.danmaku.dto.DanmakuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Danmaku", description = "danmaku management")
@RestController
@RequiredArgsConstructor
public class DanmakuController {

    private final DanmakuService danmakuService;

    @Operation(summary = "Get danmaku for a video")
    @GetMapping("/api/videos/{videoId}/danmaku")
    public Result<List<DanmakuVO>> getDanmaku(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "0") Float startTime,
            @RequestParam(defaultValue = "60") Float endTime) {
        if (startTime < 0 || endTime < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Time range must be non-negative");
        }
        if (endTime <= startTime) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "endTime must be greater than startTime");
        }
        if (endTime - startTime > 3600) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Time range cannot exceed 1 hour");
        }
        return Result.success(danmakuService.getDanmakuByVideoId(videoId, startTime, endTime));
    }

}
