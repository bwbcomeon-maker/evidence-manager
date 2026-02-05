package com.bwbcomeon.evidence.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志表
 */
@Data
public class AuditLog {

    /** 审计日志ID */
    private Long id;

    /** 操作人用户ID（登录失败可为空） */
    private Long actorUserId;

    /** 操作类型（如LOGIN_SUCCESS/LOGIN_FAIL/USER_CREATE等） */
    private String action;

    /** 对象类型（USER/PROJECT/EVIDENCE等） */
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 是否成功 */
    private Boolean success;

    /** 来源IP */
    private String ip;

    /** User-Agent */
    private String userAgent;

    /** 操作详情（JSON字符串） */
    private String detail;

    /** 操作时间 */
    private LocalDateTime createdAt;

    /** 关联项目ID（如证据作废、项目成员变更等） */
    private Long projectId;

    /** 变更前数据快照（JSON） */
    private String beforeData;

    /** 变更后数据快照（JSON） */
    private String afterData;
}
