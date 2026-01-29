package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理员-用户列表项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserListItemVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String roleCode;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
