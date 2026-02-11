package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 证据元数据表
 */
@Data
public class EvidenceItem {
    /**
     * 证据ID
     */
    private Long id;

    /**
     * 所属项目
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
     * 存储桶
     */
    private String bucket;

    /**
     * 对象路径
     */
    private String objectKey;

    /**
     * 文件类型
     */
    private String contentType;

    /**
     * 文件大小（字节）
     */
    private Long sizeBytes;

    /**
     * ETag校验标识
     */
    private String etag;

    /**
     * 证据生命周期状态：DRAFT/SUBMITTED/ARCHIVED/INVALID
     */
    private String evidenceStatus;

    /**
     * 所属阶段 delivery_stage.id
     */
    private Long stageId;

    /**
     * 证据类型编码，与 stage_evidence_template 对应
     */
    private String evidenceTypeCode;

    /**
     * 归档时间（evidence_status=ARCHIVED 时有值）
     */
    private OffsetDateTime archivedTime;

    /**
     * 作废时间（evidence_status=INVALID 时有值）
     */
    private OffsetDateTime invalidTime;

    /**
     * 上传人 sys_user.id
     */
    private Long createdByUserId;

    /**
     * 上传时间
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;

    /**
     * 误传原因
     */
    private String invalidReason;

    /**
     * 作废人 sys_user.id
     */
    private Long invalidByUserId;

    /**
     * 误传时间
     */
    private OffsetDateTime invalidAt;
}
