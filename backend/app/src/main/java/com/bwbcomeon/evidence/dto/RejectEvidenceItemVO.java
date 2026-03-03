package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 不符合项 VO（附件级）
 */
@Data
public class RejectEvidenceItemVO {

    private Long evidenceId;
    private String evidenceTitle;
    private String stageName;
    private String evidenceTypeDisplayName;
    private String rejectComment;
}
