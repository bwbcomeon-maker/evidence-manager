package com.bwbcomeon.evidence.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量为一个项目添加/调整多名成员（含项目经理 owner）
 */
@Data
public class BatchAddProjectMembersRequest {
    /** 成员列表：userId + role(owner/editor/viewer) */
    @NotEmpty(message = "members 不能为空")
    private List<AddProjectMemberRequest> members;
}
