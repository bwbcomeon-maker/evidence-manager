package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 项目待办汇总（用于项目列表页顶部工作台）
 */
@Data
public class ProjectTodoSummaryVO {

    /**
     * 当前用户可见范围内，状态为已退回（returned）的项目数量。
     */
    private long returnedCount;

    /**
     * 当前用户可见范围内，状态为待审批（pending_approval）的项目数量。
     */
    private long pendingApprovalCount;
}

