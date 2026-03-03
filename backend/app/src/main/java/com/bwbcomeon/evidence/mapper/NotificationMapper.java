package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息待办 Mapper
 */
@Mapper
public interface NotificationMapper {

    int insert(Notification entity);

    Notification selectById(@Param("id") Long id);

    /** 当前用户待办列表（按创建时间倒序） */
    List<Notification> selectByUserId(
            @Param("userId") Long userId,
            @Param("unreadOnly") Boolean unreadOnly,
            @Param("type") String type,
            @Param("limit") Integer limit
    );

    /** 标记已读 */
    int markRead(@Param("id") Long id, @Param("readAt") java.time.OffsetDateTime readAt);

    /** 标记已读（仅当该条消息属于当前用户时更新，返回更新行数 0 或 1） */
    int markReadByUser(@Param("id") Long id, @Param("userId") Long userId, @Param("readAt") java.time.OffsetDateTime readAt);

    /** 批量标记已读 */
    int markReadByIds(@Param("ids") List<Long> ids, @Param("readAt") java.time.OffsetDateTime readAt);
}
