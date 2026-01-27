package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 操作审计日志表实体类
 */
@Data
public class AuditOperationLog {
    /**
     * 日志ID (BIGSERIAL, 主键)
     */
    private Long id;

    /**
     * 操作用户
     */
    private UUID actorUserId;

    /**
     * 操作类型（upload / mark_invalid 等）
     */
    private String action;

    /**
     * 目标类型（project / evidence_item）
     */
    private String targetType;

    /**
     * 操作对象ID
     */
    private String targetId;

    /**
     * 扩展信息（JSONB格式，存储为JSON字符串）
     */
    private String detail;

    /**
     * 操作时间
     */
    private OffsetDateTime createdAt;
}
