package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** 用于成员选择器等场景的 auth_user 简要信息 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserSimpleVO {
    private UUID id;
    private String username;
    private String displayName;
}
