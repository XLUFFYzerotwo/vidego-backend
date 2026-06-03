package com.vidego.module.search;

import com.vidego.common.result.PageResult;
import com.vidego.common.result.Result;
import com.vidego.module.video.dto.VideoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Search", description = "Video full-text search")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Search videos by keyword (FULLTEXT + LIKE fallback)")
    @GetMapping
    public Result<PageResult<VideoVO>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(searchService.search(q, page, size));
    }
}
