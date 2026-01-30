package com.bwbcomeon.evidence.enums;

import com.bwbcomeon.evidence.exception.BusinessException;
import java.util.Arrays;

/**
 * 证据生命周期状态
 */
public enum EvidenceStatus {
    /** 草稿（仅保存未提交） */
    DRAFT,
    /** 已提交（进入管理/审核流程） */
    SUBMITTED,
    /** 已归档（最终有效状态） */
    ARCHIVED,
    /** 已作废（最终无效状态） */
    INVALID;

    public String getCode() {
        return name();
    }

    /**
     * 从字符串解析，非法值抛出业务异常
     */
    public static EvidenceStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(400, "证据状态不能为空");
        }
        String upper = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(e -> e.name().equals(upper))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "无效的证据状态：" + code + "，允许值：DRAFT/SUBMITTED/ARCHIVED/INVALID"));
    }

    /**
     * 校验是否允许从当前状态流转到目标状态
     */
    public void validateTransition(EvidenceStatus target) {
        if (target == null) {
            throw new BusinessException(400, "目标状态不能为空");
        }
        if (this == target) {
            throw new BusinessException(400, "证据已是" + getDisplayName() + "状态，无需重复操作");
        }
        switch (this) {
            case DRAFT:
                if (target != SUBMITTED) {
                    throw new BusinessException(400, "草稿只能提交，不能直接变更为" + target.getDisplayName());
                }
                break;
            case SUBMITTED:
                if (target != ARCHIVED && target != INVALID) {
                    throw new BusinessException(400, "已提交的证据只能归档或作废，不能变更为" + target.getDisplayName());
                }
                break;
            case ARCHIVED:
            case INVALID:
                throw new BusinessException(400, getDisplayName() + "状态不可再变更");
            default:
                throw new BusinessException(400, "未知状态：" + this);
        }
    }

    public String getDisplayName() {
        switch (this) {
            case DRAFT:     return "草稿";
            case SUBMITTED: return "已提交";
            case ARCHIVED:  return "已归档";
            case INVALID:   return "已作废";
            default:       return name();
        }
    }
}
