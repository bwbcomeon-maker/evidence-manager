package com.bwbcomeon.evidence.dto;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 证据列表项VO
 */
@Data
public class EvidenceListItemVO {
    /**
     * 证据ID
     */
    private Long evidenceId;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 证据标题（对应evidence_item.title）
     */
    private String title;

    /**
     * 业务证据类型
     */
    private String bizType;

    /**
     * 文件类型（MIME类型）
     */
    private String contentType;

    /**
     * 证据状态（兼容旧字段）
     */
    private String status;

    /**
     * 证据生命周期状态：DRAFT/SUBMITTED/ARCHIVED/INVALID
     */
    private String evidenceStatus;

    /**
     * 创建人ID
     */
    private UUID createdBy;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;

    /**
     * 最新版本信息
     */
    private LatestVersionVO latestVersion;
}
