package com.vidego.module.video;

import com.vidego.module.video.dto.UploadTokenVO;
import com.vidego.module.video.dto.VideoCreateRequest;
import com.vidego.module.video.dto.VideoVO;

import java.util.List;

public interface VideoService {

    /**
     * 获取视频上传的预签名 URL
     */
    UploadTokenVO getUploadToken(String filename, long fileSize);

    /**
     * 创建视频元数据记录
     */
    VideoVO createVideo(VideoCreateRequest request);

    /**
     * 根据 ID 获取视频详情（带 Redis 缓存）
     */
    VideoVO getVideoById(Long videoId);

    /**
     * 记录播放量（含 Redis 防刷）
     *
     * @param videoId  视频 ID
     * @param viewerId 观看者标识 "u:{userId}" 或 "ip:{address}"
     */
    void recordView(Long videoId, String viewerId);

    /**
     * 获取热门视频排行
     *
     * @param limit 返回数量
     */
    List<VideoVO> getHotVideos(int limit);

    /**
     * 删除视频
     */
    void deleteVideo(Long videoId);

    // ── 点赞 ──

    /**
     * 点赞视频
     */
    void likeVideo(Long videoId, Long userId);

    /**
     * 取消点赞视频
     */
    void unlikeVideo(Long videoId, Long userId);

    /**
     * 查询当前用户是否已点赞
     */
    boolean getLikeStatus(Long videoId, Long userId);

    // ── 收藏 ──

    /**
     * 收藏视频
     */
    void favoriteVideo(Long videoId, Long userId);

    /**
     * 取消收藏视频
     */
    void unfavoriteVideo(Long videoId, Long userId);

    /**
     * 查询当前用户是否已收藏
     */
    boolean getFavoriteStatus(Long videoId, Long userId);
}
