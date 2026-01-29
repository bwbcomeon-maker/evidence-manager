package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 管理员-新增用户请求
 */
@Data
public class AdminUserCreateRequest {
    /** 登录账号（必填，唯一） */
    private String username;
    /** 密码（可选，不传默认 Init@12345） */
    private String password;
    /** 姓名 */
    private String realName;
    /** 手机号 */
    private String phone;
    /** 邮箱 */
    private String email;
    /** 角色编码（必填，固定集合） */
    private String roleCode;
    /** 是否启用 */
    private Boolean enabled;
}
