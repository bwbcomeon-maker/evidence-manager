package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 证据版本表
 */
@Data
public class EvidenceVersion {
    /**
     * 版本记录ID
     */
    private Long id;

    /**
     * 所属证据ID
     */
    private Long evidenceId;

    /**
     * 所属项目ID
     */
    private Long projectId;

    /**
     * 版本号（从1开始递增）
     */
    private Integer versionNo;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 文件存储相对路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型（MIME类型）
     */
    private String contentType;

    /**
     * 上传人 sys_user.id
     */
    private Long uploaderUserId;

    /**
     * 版本备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
