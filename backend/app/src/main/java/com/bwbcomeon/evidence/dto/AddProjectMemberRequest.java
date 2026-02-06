package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 添加/调整项目成员请求
 */
@Data
public class AddProjectMemberRequest {
    /** sys_user.id */
    private Long userId;
    /** owner / editor / viewer */
    private String role;
}
