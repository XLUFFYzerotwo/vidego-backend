package com.vidego.module.feed;

import com.vidego.common.result.PageResult;
import com.vidego.module.video.dto.VideoVO;

public interface FeedService {

    /**
     * 首页推荐 Feed：
     * 第 1 页 = 热门视频（缓存），后续页 = 最新视频（分页）
     */
    PageResult<VideoVO> getRecommendedFeed(int page, int size);

    /**
     * 最新视频列表（分页）
     */
    PageResult<VideoVO> getLatestVideos(int page, int size);

    /**
     * 按标签分类推荐视频（分页）
     */
    PageResult<VideoVO> getVideosByTag(String tagName, int page, int size);
}
