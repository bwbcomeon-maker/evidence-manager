package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.TodoItemVO;

import java.util.List;

/**
 * 待办/消息服务
 */
public interface NotificationService {

    /**
     * 当前用户待办列表（支持未读筛选、类型、条数限制）
     */
    List<TodoItemVO> listTodos(Long userId, Boolean unreadOnly, String type, Integer limit);

    /**
     * 单条标记已读
     */
    void markRead(Long userId, Long notificationId);

    /**
     * 批量标记已读
     */
    void markReadByIds(Long userId, List<Long> notificationIds);
}
