package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 管理员-修改用户请求（不允许改 password_hash / username）
 */
@Data
public class AdminUserUpdateRequest {
    /** 姓名 */
    private String realName;
    /** 手机号 */
    private String phone;
    /** 邮箱 */
    private String email;
    /** 角色编码（固定集合） */
    private String roleCode;
    /** 是否启用 */
    private Boolean enabled;
}
