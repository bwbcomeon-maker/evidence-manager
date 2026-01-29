package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/当前用户返回数据（id, username, realName, roleCode, enabled）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserVO {
    private Long id;
    private String username;
    private String realName;
    private String roleCode;
    private Boolean enabled;
}
