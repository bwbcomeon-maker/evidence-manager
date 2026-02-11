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
     * 所属阶段 ID delivery_stage.id（必填）
     */
    private Long stageId;

    /**
     * 证据类型编码，须属于该阶段模板（必填）
     */
    private String evidenceTypeCode;

    /**
     * 备注（可选）
     */
    private String remark;
}
