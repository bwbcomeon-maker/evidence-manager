package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 证据元数据表实体类
 */
@Data
public class EvidenceItem {
    /**
     * 证据ID (BIGSERIAL, 主键)
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
     * 补充说明
     */
    private String note;

    /**
     * MinIO Bucket
     */
    private String bucket;

    /**
     * MinIO Key
     */
    private String objectKey;

    /**
     * MIME 类型
     */
    private String contentType;

    /**
     * 字节大小
     */
    private Long sizeBytes;

    /**
     * 校验标识
     */
    private String etag;

    /**
     * 状态（active / invalid / archived）
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
     * 误传说明
     */
    private String invalidReason;

    /**
     * 操作人
     */
    private UUID invalidBy;

    /**
     * 操作时间
     */
    private OffsetDateTime invalidAt;
}
