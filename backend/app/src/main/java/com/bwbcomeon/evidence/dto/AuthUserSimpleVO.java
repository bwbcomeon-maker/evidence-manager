package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用于成员选择器等场景的 sys_user 简要信息 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserSimpleVO {
    private Long id;
    private String username;
    private String displayName;
}
