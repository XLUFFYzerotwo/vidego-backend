package com.vidego.module.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vidego.common.exception.BusinessException;
import com.vidego.common.result.ErrorCode;
import com.vidego.common.result.PageResult;
import com.vidego.module.notification.dto.NotificationVO;
import com.vidego.module.notification.entity.Notification;
import com.vidego.module.notification.mapper.NotificationMapper;
import com.vidego.module.user.entity.User;
import com.vidego.module.user.mapper.UserMapper;
import com.vidego.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 通知服务实现
 *
 * <p>注意：通知的创建由 {@link com.vidego.module.notification.mq.NotificationConsumer}
 * 异步完成，本服务只负责查询和已读状态变更。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PageResult<NotificationVO> getNotifications(Long userId, int page, int size) {
        Page<Notification> notificationPage = notificationMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt));

        if (notificationPage.getRecords().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, size);
        }

        List<NotificationVO> vos = notificationPage.getRecords().stream()
                .map(this::toVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new PageResult<>(vos, notificationPage.getTotal(),
                (int) notificationPage.getCurrent(), (int) notificationPage.getSize());
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "notification not found");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "cannot mark others' notification as read");
        }

        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .set(Notification::getIsRead, 1)
                .eq(Notification::getId, notificationId));

        log.debug("Notification marked as read: id={}, userId={}", notificationId, userId);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .set(Notification::getIsRead, 1)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));

        log.debug("All notifications marked as read: userId={}", userId);
    }

    // ── 私有方法 ──

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        User user = userMapper.selectById(notification.getFromUserId());

        vo.setId(notification.getId());
        vo.setType(notification.getType());
        vo.setSubType(notification.getSubType());
        vo.setVideoId(notification.getVideoId());
        vo.setCommentId(notification.getCommentId());
        vo.setContent(notification.getContent());
        vo.setIsRead(notification.getIsRead());
        vo.setCreatedAt(notification.getCreatedAt() != null
                ? notification.getCreatedAt().format(DTF) : null);
        vo.setFromUser(toUserVO(user));
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
