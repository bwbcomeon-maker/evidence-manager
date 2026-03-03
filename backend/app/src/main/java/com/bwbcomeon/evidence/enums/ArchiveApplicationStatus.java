package com.bwbcomeon.evidence.enums;

import com.bwbcomeon.evidence.exception.BusinessException;
import java.util.Arrays;

/**
 * 归档申请单状态
 */
public enum ArchiveApplicationStatus {
    /** 待审批（已提交，等待 PMO/管理员审批） */
    PENDING_APPROVAL,
    /** 已通过（审批通过，项目已执行归档） */
    APPROVED,
    /** 已退回（审批不通过，退回给项目经理修改） */
    REJECTED;

    public String getCode() {
        return name();
    }

    /**
     * 从字符串解析，非法值抛出业务异常
     */
    public static ArchiveApplicationStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(400, "归档申请状态不能为空");
        }
        String upper = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(e -> e.name().equals(upper))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400,
                        "无效的归档申请状态：" + code + "，允许值：PENDING_APPROVAL/APPROVED/REJECTED"));
    }

    public String getDisplayName() {
        switch (this) {
            case PENDING_APPROVAL:
                return "待审批";
            case APPROVED:
                return "已通过";
            case REJECTED:
                return "已退回";
            default:
                return name();
        }
    }
}
