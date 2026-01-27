package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

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
     * 状态：active / invalid / archived
     */
    private String status;

    /**
     * 上传人
     */
    private UUID createdBy;

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
     * 误传人
     */
    private UUID invalidBy;

    /**
     * 误传时间
     */
    private OffsetDateTime invalidAt;
}
