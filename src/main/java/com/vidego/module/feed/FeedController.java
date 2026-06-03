package com.vidego.module.feed;

import com.vidego.common.result.PageResult;
import com.vidego.common.result.Result;
import com.vidego.module.video.dto.VideoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Feed", description = "Homepage feed, recommended and latest videos")
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    @Operation(summary = "Recommended feed (page 1 cached hot mix, pages 2+ latest)")
    @GetMapping("/recommended")
    public Result<PageResult<VideoVO>> getRecommended(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(feedService.getRecommendedFeed(page, size));
    }

    @Operation(summary = "Latest videos (paginated)")
    @GetMapping("/latest")
    public Result<PageResult<VideoVO>> getLatest(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(feedService.getLatestVideos(page, size));
    }

    @Operation(summary = "Videos by tag")
    @GetMapping("/by-tag")
    public Result<PageResult<VideoVO>> getByTag(
            @RequestParam String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(feedService.getVideosByTag(tag, page, size));
    }
}
