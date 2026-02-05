package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 项目成员（ACL + 用户信息）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberVO {
    private UUID userId;
    private String role;
    private String username;
    private String displayName;
    /** 是否为当前登录用户（用于前端隐藏“编辑/移除”自己的入口） */
    private Boolean isCurrentUser;
}
