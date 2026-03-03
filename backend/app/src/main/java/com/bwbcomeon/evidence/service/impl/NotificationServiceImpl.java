package com.bwbcomeon.evidence.service.impl;

import com.bwbcomeon.evidence.dto.TodoItemVO;
import com.bwbcomeon.evidence.entity.Notification;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.NotificationMapper;
import com.bwbcomeon.evidence.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 待办/消息服务实现
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Autowired
    private NotificationMapper notificationMapper;

    @Override
    public List<TodoItemVO> listTodos(Long userId, Boolean unreadOnly, String type, Integer limit) {
        if (userId == null) {
            return new ArrayList<>();
        }
        List<Notification> list = notificationMapper.selectByUserId(userId, unreadOnly, type, limit != null ? limit : 100);
        List<TodoItemVO> result = new ArrayList<>(list.size());
        for (Notification n : list) {
            TodoItemVO vo = new TodoItemVO();
            vo.setId(n.getId());
            vo.setType(n.getType());
            vo.setTitle(n.getTitle());
            vo.setBody(n.getBody());
            vo.setRelatedProjectId(n.getRelatedProjectId());
            vo.setRelatedApplicationId(n.getRelatedApplicationId());
            vo.setLinkPath(n.getLinkPath());
            vo.setReadAt(n.getReadAt());
            vo.setCreatedAt(n.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    @Override
    public void markRead(Long userId, Long notificationId) {
        if (userId == null || notificationId == null) {
            throw new BusinessException(400, "参数不能为空");
        }
        log.info("markRead: userId={}, notificationId={}", userId, notificationId);
        Notification n = notificationMapper.selectById(notificationId);
        if (n == null) {
            throw new BusinessException(404, "消息不存在");
        }
        int updated = notificationMapper.markReadByUser(notificationId, userId, OffsetDateTime.now());
        if (updated == 0) {
            Long ownerId = n.getUserId();
            log.warn("markRead 403: notificationId={}, currentUserId={}, notificationUserId={}",
                    notificationId, userId, ownerId);
            throw new BusinessException(403, "无权限操作该消息");
        }
    }

    @Override
    public void markReadByIds(Long userId, List<Long> notificationIds) {
        if (userId == null || notificationIds == null || notificationIds.isEmpty()) {
            throw new BusinessException(400, "参数不能为空");
        }
        for (Long id : notificationIds) {
            Notification n = notificationMapper.selectById(id);
            if (n != null && userId.equals(n.getUserId())) {
                notificationMapper.markRead(id, OffsetDateTime.now());
            }
        }
    }
}
