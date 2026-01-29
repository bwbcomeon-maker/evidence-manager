package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 证据上传请求DTO
 */
@Data
public class EvidenceUploadRequest {
    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 证据名称
     */
    private String name;

    /**
     * 证据类型
     */
    private String type;

    /**
     * 备注（可选）
     */
    private String remark;
}
