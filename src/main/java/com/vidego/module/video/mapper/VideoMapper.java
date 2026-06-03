package com.vidego.module.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vidego.module.video.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface VideoMapper extends BaseMapper<Video> {

    /**
     * 原子增加播放量
     */
    @Update("UPDATE video SET view_count = view_count + 1 WHERE id = #{videoId}")
    int incrementViewCount(@Param("videoId") Long videoId);

    /**
     * 原子增加点赞数
     */
    @Update("UPDATE video SET like_count = like_count + 1 WHERE id = #{videoId}")
    int incrementLikeCount(@Param("videoId") Long videoId);

    /**
     * 原子减少点赞数（不低于 0）
     */
    @Update("UPDATE video SET like_count = like_count - 1 WHERE id = #{videoId} AND like_count > 0")
    int decrementLikeCount(@Param("videoId") Long videoId);

    /**
     * 热门视频排行：按综合热度（播放量×0.7 + 点赞×0.2 + 评论×0.1）降序
     */
    @Select("SELECT * FROM video WHERE status = 1 " +
            "ORDER BY (view_count * 0.7 + like_count * 0.2 + comment_count * 0.1) DESC " +
            "LIMIT #{limit}")
    List<Video> selectHotVideos(@Param("limit") int limit);
}
