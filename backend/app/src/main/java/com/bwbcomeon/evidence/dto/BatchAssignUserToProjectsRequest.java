package com.bwbcomeon.evidence.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量将一人分配至多个项目（PMO/系统管理员使用）
 */
@Data
public class BatchAssignUserToProjectsRequest {
    /** 被分配用户 sys_user.id */
    @NotNull(message = "userId 不能为空")
    private Long userId;
    /** 项目 id 列表 */
    @NotEmpty(message = "projectIds 不能为空")
    private List<Long> projectIds;
    /** 角色：owner（项目经理）/ editor / viewer */
    private String role = "editor";
}
