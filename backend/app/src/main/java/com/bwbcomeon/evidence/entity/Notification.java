package com.bwbcomeon.evidence.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户消息待办表 notification
 */
@Data
public class Notification {

    /** 消息ID */
    private Long id;
    /** 接收人 sys_user.id */
    private Long userId;
    /** 类型：如 ARCHIVE_PENDING / ARCHIVE_RETURNED */
    private String type;
    /** 标题 */
    private String title;
    /** 正文 */
    private String body;
    /** 关联项目ID */
    private Long relatedProjectId;
    /** 关联归档申请单ID */
    private Long relatedApplicationId;
    /** 前端跳转路径，如 /projects/123?tab=evidence */
    private String linkPath;
    /** 已读时间 */
    private OffsetDateTime readAt;
    /** 创建时间 */
    private OffsetDateTime createdAt;
}
