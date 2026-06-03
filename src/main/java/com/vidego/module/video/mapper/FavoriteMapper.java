package com.vidego.module.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vidego.module.video.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
