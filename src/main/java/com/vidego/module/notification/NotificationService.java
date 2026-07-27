package com.vidego.module.notification;

import com.vidego.common.result.PageResult;
import com.vidego.module.notification.dto.NotificationVO;

/**
 * 通知服务接口
 *
 * <p>提供通知列表分页查询、未读计数、已读标记等功能。
 * 通知的写入由 {@link com.vidego.module.notification.mq.NotificationConsumer} 异步完成，
 * 本服务只负责读取和状态变更。</p>
 */
public interface NotificationService {

    /**
     * 分页查询当前用户的通知列表（按时间倒序）
     *
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     */
    PageResult<NotificationVO> getNotifications(Long userId, int page, int size);

    /**
     * 获取当前用户未读通知数量
     */
    long getUnreadCount(Long userId);

    /**
     * 标记单条通知为已读
     *
     * <p>仅通知接收者本人可标记。</p>
     */
    void markAsRead(Long notificationId, Long userId);

    /**
     * 将当前用户所有未读通知标记为已读
     */
    void markAllAsRead(Long userId);
}
