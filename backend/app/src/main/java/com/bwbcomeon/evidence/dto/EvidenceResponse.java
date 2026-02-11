package com.bwbcomeon.evidence.dto;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 证据响应DTO
 */
@Data
public class EvidenceResponse {
    /**
     * 证据ID
     */
    private Long id;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 证据标题
     */
    private String title;

    /**
     * 证据说明
     */
    private String note;

    /**
     * 文件类型
     */
    private String contentType;

    /**
     * 文件大小（字节）
     */
    private Long sizeBytes;

    /**
     * 证据生命周期状态：DRAFT/SUBMITTED/ARCHIVED/INVALID
     */
    private String evidenceStatus;

    /**
     * 所属阶段 ID delivery_stage.id
     */
    private Long stageId;

    /**
     * 证据类型编码
     */
    private String evidenceTypeCode;

    /**
     * 上传人 sys_user.id
     */
    private Long createdByUserId;

    /**
     * 上传时间
     */
    private OffsetDateTime createdAt;
}
