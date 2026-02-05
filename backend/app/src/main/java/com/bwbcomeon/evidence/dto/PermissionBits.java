package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V1 统一权限位（与后端校验同源，前端仅读此控制按钮显隐）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionBits {
    private Boolean canUpload;
    private Boolean canSubmit;
    private Boolean canArchive;
    private Boolean canInvalidate;
    private Boolean canManageMembers;

    public static PermissionBits all(boolean value) {
        return new PermissionBits(value, value, value, value, value);
    }
}
