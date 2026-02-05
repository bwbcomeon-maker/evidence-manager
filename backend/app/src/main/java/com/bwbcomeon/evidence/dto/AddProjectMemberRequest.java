package com.bwbcomeon.evidence.dto;

import lombok.Data;

import java.util.UUID;

/**
 * 添加/调整项目成员请求
 */
@Data
public class AddProjectMemberRequest {
    /** auth_user.id */
    private UUID userId;
    /** owner / editor / viewer */
    private String role;
}
