package com.bwbcomeon.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 归档退回请求：整体意见 + 可选附件级不符合项
 */
@Data
public class ArchiveRejectRequest {

    /** 退回意见全文（必填） */
    @NotBlank(message = "退回意见不能为空")
    private String comment;

    /** 附件级不符合项（可选），列表元素为 evidenceId + comment */
    private List<EvidenceRejectItem> evidenceComments;

    /**
     * 单条证据不符合项
     */
    @Data
    public static class EvidenceRejectItem {
        private Long evidenceId;
        private String comment;
    }
}
