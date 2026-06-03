package com.vidego.module.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vidego.module.comment.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    @Update("UPDATE comment SET like_count = like_count + 1 WHERE id = #{commentId}")
    int incrementLikeCount(Long commentId);

    @Update("UPDATE comment SET like_count = like_count - 1 WHERE id = #{commentId} AND like_count > 0")
    int decrementLikeCount(Long commentId);
}
