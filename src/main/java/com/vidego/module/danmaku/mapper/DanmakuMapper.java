package com.vidego.module.danmaku.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vidego.module.danmaku.entity.Danmaku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DanmakuMapper extends BaseMapper<Danmaku> {
    List<Danmaku> selectByVideoIdAndTimeRange(
            @Param("videoId") Long videoId,
            @Param("startTime") Float startTime,
            @Param("endTime") Float endTime);
}