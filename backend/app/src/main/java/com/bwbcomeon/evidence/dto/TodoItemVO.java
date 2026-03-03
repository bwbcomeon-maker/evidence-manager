package com.bwbcomeon.evidence.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 待办/消息项 VO
 */
@Data
public class TodoItemVO {

    private Long id;
    /** 类型：ARCHIVE_PENDING / ARCHIVE_RETURNED 等 */
    private String type;
    private String title;
    private String body;
    private Long relatedProjectId;
    private Long relatedApplicationId;
    /** 前端跳转路径，如 /projects/123?tab=evidence */
    private String linkPath;
    private OffsetDateTime readAt;
    private OffsetDateTime createdAt;
}
