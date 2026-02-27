package com.bwbcomeon.evidence.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 全局证据搜索结果 VO，用于前端展示及跳转到项目详情并定位到具体证据项。
 * 必须包含 stageCode、evidenceTypeCode 等前端跳转定位所需字段。
 */
@Data
public class EvidenceSearchResultVO {

    /** 证据ID */
    private Long evidenceId;

    /** 项目ID */
    private Long projectId;

    /** 项目名称（用于列表展示） */
    private String projectName;

    /** 所属阶段编码（如 S1/S5），前端定位 evidence-card 必需 */
    private String stageCode;

    /** 所属阶段名称 */
    private String stageName;

    /** 证据类型编码，与 stage_evidence_template 对应，前端定位必需 */
    private String evidenceTypeCode;

    /** 证据类型展示名（stage_evidence_template.display_name） */
    private String evidenceTypeDisplayName;

    /** 证据标题 */
    private String title;

    /** 上传人展示名（real_name 或 username） */
    private String createdByDisplayName;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 证据状态：DRAFT/SUBMITTED/ARCHIVED/INVALID */
    private String evidenceStatus;

    /** 最新版本信息（可选，用于列表展示文件名等） */
    private LatestVersionVO latestVersion;
}
