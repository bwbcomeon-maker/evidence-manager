package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 操作审计日志表（操作人为 sys_user.id）
 */
@Data
public class AuditOperationLog {
    /**
     * 日志ID
     */
    private Long id;

    /**
     * 操作人 sys_user.id
     */
    private Long actorUserId;

    /**
     * 操作类型：upload / mark_invalid 等
     */
    private String action;

    /**
     * 目标类型：project / evidence_item
     */
    private String targetType;

    /**
     * 操作对象ID
     */
    private String targetId;

    /**
     * 扩展信息（JSON格式字符串）
     */
    private String detail;

    /**
     * 操作时间
     */
    private OffsetDateTime createdAt;
}
