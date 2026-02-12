package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.entity.DeliveryStage;
import com.bwbcomeon.evidence.entity.EvidenceItem;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.entity.SysUser;
import com.bwbcomeon.evidence.mapper.DeliveryStageMapper;
import com.bwbcomeon.evidence.mapper.EvidenceItemMapper;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
import com.bwbcomeon.evidence.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 阶段进度测试用 fixture：创建项目、插入有效证据、作废证据等。仅用于测试。
 */
@Component
public class StageProgressTestFixture {

    private final ProjectMapper projectMapper;
    private final DeliveryStageMapper deliveryStageMapper;
    private final EvidenceItemMapper evidenceItemMapper;
    private final SysUserMapper sysUserMapper;

    public StageProgressTestFixture(ProjectMapper projectMapper,
                                   DeliveryStageMapper deliveryStageMapper,
                                   EvidenceItemMapper evidenceItemMapper,
                                   SysUserMapper sysUserMapper) {
        this.projectMapper = projectMapper;
        this.deliveryStageMapper = deliveryStageMapper;
        this.evidenceItemMapper = evidenceItemMapper;
        this.sysUserMapper = sysUserMapper;
    }

    /** 取第一个系统用户 ID，用于 project.created_by_user_id 与 evidence_item.created_by_user_id */
    public long getAnyUserId() {
        List<SysUser> users = sysUserMapper.selectAll();
        if (users == null || users.isEmpty()) {
            throw new IllegalStateException("Test requires at least one sys_user");
        }
        return users.get(0).getId();
    }

    /** 按阶段编码取 stage_id */
    public long getStageIdByCode(String code) {
        DeliveryStage stage = deliveryStageMapper.selectByCode(code);
        if (stage == null) {
            throw new IllegalStateException("No delivery_stage for code: " + code);
        }
        return stage.getId();
    }

    /**
     * 创建项目（未归档），返回 projectId。
     */
    public long createProject(boolean hasProcurement) {
        long userId = getAnyUserId();
        Project p = new Project();
        p.setCode("TEST-P" + System.currentTimeMillis());
        p.setName("Test Project");
        p.setDescription("Fixture");
        p.setStatus("active");
        p.setHasProcurement(hasProcurement);
        p.setCreatedByUserId(userId);
        projectMapper.insert(p);
        return p.getId();
    }

    /**
     * 插入一条证据。
     * 门禁口径（currentCount / completed）：仅 SUBMITTED/ARCHIVED 计入；
     * 展示口径（uploadCount）：DRAFT/SUBMITTED/ARCHIVED 均计入。
     * evidenceStatus 由调用方传入，返回 evidence_item.id。
     */
    public long insertEvidence(long projectId, long stageId, String evidenceTypeCode, String evidenceStatus) {
        long userId = getAnyUserId();
        EvidenceItem item = new EvidenceItem();
        item.setProjectId(projectId);
        item.setTitle("test-" + evidenceTypeCode);
        item.setBucket("default");
        item.setObjectKey("test/key");
        item.setSizeBytes(1L);
        item.setEvidenceStatus(evidenceStatus);
        item.setStageId(stageId);
        item.setEvidenceTypeCode(evidenceTypeCode);
        item.setCreatedByUserId(userId);
        evidenceItemMapper.insert(item);
        return item.getId();
    }

    /** 将证据作废（INVALID），作废后不计入 currentCount */
    public void invalidateEvidence(long evidenceId) {
        OffsetDateTime now = OffsetDateTime.now();
        evidenceItemMapper.updateEvidenceInvalidate(
                evidenceId,
                "INVALID",
                now,
                "测试作废",
                getAnyUserId(),
                now
        );
    }
}
