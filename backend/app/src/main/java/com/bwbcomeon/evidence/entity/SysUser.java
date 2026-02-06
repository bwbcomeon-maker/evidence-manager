package com.bwbcomeon.evidence.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户表
 */
@Data
public class SysUser {

    /** 用户主键ID */
    private Long id;

    /** 登录账号（唯一） */
    private String username;

    /** 密码哈希（BCrypt） */
    private String passwordHash;

    /** 姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 角色编码（SYSTEM_ADMIN/PMO/AUDITOR/USER） */
    private String roleCode;

    /** 是否启用（禁用不可登录） */
    private Boolean enabled;

    /** 是否逻辑删除 */
    private Boolean isDeleted;

    /** 最近登录时间 */
    private LocalDateTime lastLoginAt;

    /** 最近登录IP */
    private String lastLoginIp;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
