package com.vidego.module.search;

import com.vidego.common.result.PageResult;
import com.vidego.module.video.dto.VideoVO;

public interface SearchService {

    /**
     * 全文搜索视频（标题 + 描述）
     *
     * @param keyword 搜索关键词
     * @param page    页码
     * @param size    每页条数
     */
    PageResult<VideoVO> search(String keyword, int page, int size);
}
