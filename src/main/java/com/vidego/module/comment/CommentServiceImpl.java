package com.vidego.module.comment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vidego.common.exception.BusinessException;
import com.vidego.common.result.ErrorCode;
import com.vidego.common.result.PageResult;
import com.vidego.module.comment.dto.CommentCreateRequest;
import com.vidego.module.comment.dto.CommentVO;
import com.vidego.module.comment.entity.Comment;
import com.vidego.module.comment.mapper.CommentMapper;
import com.vidego.module.video.entity.LikeRecord;
import com.vidego.module.video.mapper.LikeRecordMapper;
import com.vidego.module.user.entity.User;
import com.vidego.module.user.mapper.UserMapper;
import com.vidego.module.user.vo.UserVO;
import com.vidego.module.video.entity.Video;
import com.vidego.module.video.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final UserMapper userMapper;
    private final VideoMapper videoMapper;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 点赞对象类型 */
    private static final String TARGET_TYPE_COMMENT = "comment";

    @Override
    public PageResult<CommentVO> getComments(Long videoId, int page, int size) {
        // 校验视频存在
        if (videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                .eq(Video::getId, videoId)) == 0) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        // 1. 分页查询根评论（parent_id IS NULL），按时间降序
        Page<Comment> rootPage = commentMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getVideoId, videoId)
                        .isNull(Comment::getParentId)
                        .orderByDesc(Comment::getCreatedAt));

        if (rootPage.getRecords().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, size);
        }

        // 2. 批量查询子回复
        Set<Long> rootIds = rootPage.getRecords().stream()
                .map(Comment::getId).collect(Collectors.toSet());
        List<Comment> replies = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .in(Comment::getParentId, rootIds)
                        .orderByAsc(Comment::getCreatedAt));

        // 3. 收集所有需要查询的用户 ID（去重）
        Set<Long> userIds = new HashSet<>();
        rootPage.getRecords().forEach(c -> userIds.add(c.getUserId()));
        replies.forEach(c -> userIds.add(c.getUserId()));
        Map<Long, UserVO> userMap = batchQueryUsers(userIds);

        // 4. 构建回复映射
        Map<Long, List<CommentVO>> replyMap = new LinkedHashMap<>();
        for (Comment reply : replies) {
            CommentVO replyVO = toCommentVO(reply, userMap);
            replyMap.computeIfAbsent(reply.getParentId(), k -> new ArrayList<>()).add(replyVO);
        }

        // 5. 构建根评论 VO（带回复）
        List<CommentVO> vos = rootPage.getRecords().stream()
                .map(c -> {
                    CommentVO vo = toCommentVO(c, userMap);
                    vo.setReplies(replyMap.getOrDefault(c.getId(), Collections.emptyList()));
                    return vo;
                })
                .collect(Collectors.toList());

        return new PageResult<>(vos, rootPage.getTotal(),
                (int) rootPage.getCurrent(), (int) rootPage.getSize());
    }

    @Override
    @Transactional
    public CommentVO createComment(Long videoId, Long userId, CommentCreateRequest request) {
        // 校验视频存在
        if (videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                .eq(Video::getId, videoId)) == 0) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        // 如果是回复，校验父评论存在且属于同一视频
        if (request.getParentId() != null) {
            Comment parent = commentMapper.selectById(request.getParentId());
            if (parent == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "parent comment not found");
            }
            if (!parent.getVideoId().equals(videoId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "reply must belong to the same video");
            }
        }

        // 创建评论
        Comment comment = new Comment();
        comment.setVideoId(videoId);
        comment.setUserId(userId);
        comment.setParentId(request.getParentId());
        comment.setContent(request.getContent().trim());
        comment.setLikeCount(0);
        commentMapper.insert(comment);

        // 更新视频评论计数
        videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .setSql("comment_count = comment_count + 1")
                .eq(Video::getId, videoId));

        log.info("Comment created: id={}, videoId={}, userId={}",
                comment.getId(), videoId, userId);

        // 查询用户信息构建 VO
        User user = userMapper.selectById(userId);
        UserVO userVO = toUserVO(user);

        CommentVO vo = toCommentVO(comment, Map.of(userId, userVO));
        if (request.getParentId() == null) {
            vo.setReplies(Collections.emptyList());
        }
        return vo;
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "comment not found");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "you can only delete your own comments");
        }

        // 如果是根评论，先删子回复
        if (comment.getParentId() == null) {
            List<Comment> replies = commentMapper.selectList(
                    new LambdaQueryWrapper<Comment>()
                            .eq(Comment::getParentId, commentId));
            if (!replies.isEmpty()) {
                List<Long> replyIds = replies.stream().map(Comment::getId).collect(Collectors.toList());
                // 删除子回复的点赞记录
                likeRecordMapper.delete(new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getTargetType, TARGET_TYPE_COMMENT)
                        .in(LikeRecord::getTargetId, replyIds));
                commentMapper.deleteBatchIds(replyIds);
            }
        } else {
            // 是子回复，删除其点赞记录
            likeRecordMapper.delete(new LambdaQueryWrapper<LikeRecord>()
                    .eq(LikeRecord::getTargetType, TARGET_TYPE_COMMENT)
                    .eq(LikeRecord::getTargetId, commentId));
        }

        // 删除评论本身的点赞记录 + 评论
        likeRecordMapper.delete(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getTargetType, TARGET_TYPE_COMMENT)
                .eq(LikeRecord::getTargetId, commentId));
        commentMapper.deleteById(commentId);

        // 更新视频评论计数
        videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .setSql("comment_count = comment_count - 1")
                .eq(Video::getId, comment.getVideoId())
                .gt(Video::getCommentCount, 0));

        log.info("Comment deleted: id={}, userId={}", commentId, userId);
    }

    @Override
    @Transactional
    public void likeComment(Long commentId, Long userId) {
        // 校验评论存在
        commentMapper.selectById(commentId);
        if (!commentExists(commentId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "comment not found");
        }

        // 查是否已点赞
        Long count = likeRecordMapper.selectCount(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetType, TARGET_TYPE_COMMENT)
                .eq(LikeRecord::getTargetId, commentId));
        if (count > 0) {
            return; // 已点赞，幂等
        }

        // 插入点赞记录
        LikeRecord record = new LikeRecord();
        record.setUserId(userId);
        record.setTargetType(TARGET_TYPE_COMMENT);
        record.setTargetId(commentId);
        likeRecordMapper.insert(record);

        commentMapper.incrementLikeCount(commentId);
        log.debug("Comment liked: commentId={}, userId={}", commentId, userId);
    }

    @Override
    @Transactional
    public void unlikeComment(Long commentId, Long userId) {
        // 删除点赞记录
        int deleted = likeRecordMapper.delete(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetType, TARGET_TYPE_COMMENT)
                .eq(LikeRecord::getTargetId, commentId));
        if (deleted > 0) {
            commentMapper.decrementLikeCount(commentId);
            log.debug("Comment unliked: commentId={}, userId={}", commentId, userId);
        }
    }

    // ══════════════════════════════════════════════
    //  私有方法
    // ══════════════════════════════════════════════

    private boolean commentExists(Long commentId) {
        return commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().eq(Comment::getId, commentId)) > 0;
    }

    /**
     * 批量查询用户信息并转为 VO
     */
    private Map<Long, UserVO> batchQueryUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, UserVO> map = new HashMap<>();
        for (User user : users) {
            map.put(user.getId(), toUserVO(user));
        }
        return map;
    }

    private CommentVO toCommentVO(Comment comment, Map<Long, UserVO> userMap) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setVideoId(comment.getVideoId());
        vo.setUserId(comment.getUserId());
        vo.setParentId(comment.getParentId());
        vo.setContent(comment.getContent());
        vo.setLikeCount(comment.getLikeCount());
        vo.setCreatedAt(comment.getCreatedAt() != null
                ? comment.getCreatedAt().format(DTF) : null);
        vo.setUser(userMap.get(comment.getUserId()));
        return vo;
    }

    private UserVO toUserVO(User user) {
        if (user == null) return null;
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return vo;
    }
}
