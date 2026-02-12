package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.StageItemVO;
import com.bwbcomeon.evidence.dto.StageProgressVO;
import com.bwbcomeon.evidence.dto.StageVO;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段进度与门禁核心逻辑：参与计算项过滤、x/y、overall%、keyMissing、healthStatus、canArchive
 */
@SpringBootTest
@Transactional
@Import(StageProgressTestFixture.class)
class StageProgressServiceTest {

    @Autowired
    private StageProgressService stageProgressService;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private StageProgressTestFixture fixture;
    @Test
    void computeStageProgress_hasProcurementFalse_s1Has3Items() {
        Project p = projectMapper.selectById(1L);
        if (p == null) return;
        p.setHasProcurement(false);
        projectMapper.update(p);

        StageProgressVO vo = stageProgressService.computeStageProgress(1L);
        assertThat(vo).isNotNull();
        Optional<StageVO> s1 = vo.getStages().stream().filter(s -> "S1".equals(s.getStageCode())).findFirst();
        assertThat(s1).isPresent();
        assertThat(s1.get().getItemCount()).isEqualTo(3);
        assertThat(s1.get().getItems().stream().anyMatch(i -> "S1_PRODUCT_COMPARE".equals(i.getEvidenceTypeCode()))).isFalse();
    }

    @Test
    void computeStageProgress_hasProcurementTrue_s1Has4Items() {
        Project p = projectMapper.selectById(1L);
        if (p == null) return;
        p.setHasProcurement(true);
        projectMapper.update(p);

        StageProgressVO vo = stageProgressService.computeStageProgress(1L);
        assertThat(vo).isNotNull();
        Optional<StageVO> s1 = vo.getStages().stream().filter(s -> "S1".equals(s.getStageCode())).findFirst();
        assertThat(s1).isPresent();
        assertThat(s1.get().getItemCount()).isEqualTo(4);
        assertThat(s1.get().getItems().stream().anyMatch(i -> "S1_PRODUCT_COMPARE".equals(i.getEvidenceTypeCode()))).isTrue();
    }

    @Test
    void computeStageProgress_returnsStagesInOrder() {
        Long anyProjectId = projectMapper.selectAll().stream().findFirst().map(Project::getId).orElse(null);
        if (anyProjectId == null) return;
        StageProgressVO vo = stageProgressService.computeStageProgress(anyProjectId);
        assertThat(vo).isNotNull();
        assertThat(vo.getStages()).isNotNull().isNotEmpty();
        assertThat(vo.getStages()).extracting(StageVO::getStageCode).containsExactly("S1", "S2", "S3", "S4", "S5");
    }

    // ---------- P0 边界用例：锁死统计与门禁逻辑 ----------

    /**
     * 【用例 1】S5 二选一：上传一项后作废 → 组完成应立即变为 false，keyMissing 仅 1 条组级缺失。
     * Arrange: 创建 project；S1～S4 打满证据使无缺失，S5 插入 1 条 SUBMITTED 后作废，使唯一缺失为 S5 组。
     * Act: 第一次 computeStageProgress（S5 组完成）→ 作废 S5 证据 → 第二次 computeStageProgress。
     * Assert: 第一次 S5 组已完成；第二次 S5 completedCount=0、groupCompleted=false；keyMissing 仅含 1 条且为组级文案（不能出现两条单项缺失）。
     */
    @Test
    void s5Group_invalidateSingleEvidence_groupCompletedBecomesFalse_keyMissingSingleGroupEntry() {
        long projectId = fixture.createProject(false);
        long s1 = fixture.getStageIdByCode("S1");
        long s2 = fixture.getStageIdByCode("S2");
        long s3 = fixture.getStageIdByCode("S3");
        long s4 = fixture.getStageIdByCode("S4");
        long s5Id = fixture.getStageIdByCode("S5");

        fixture.insertEvidence(projectId, s1, "S1_START_PHOTO", "SUBMITTED");
        fixture.insertEvidence(projectId, s1, "S1_START_REPORT", "SUBMITTED");
        fixture.insertEvidence(projectId, s1, "S1_IMPL_PLAN", "SUBMITTED");
        fixture.insertEvidence(projectId, s2, "S2_LOGISTICS_SIGNED", "SUBMITTED");
        for (int i = 0; i < 3; i++) fixture.insertEvidence(projectId, s2, "S2_ARRIVAL_PHOTO", "SUBMITTED");
        fixture.insertEvidence(projectId, s2, "S2_PACKAGE_PHOTO", "SUBMITTED");
        fixture.insertEvidence(projectId, s2, "S2_NAME_PLATE", "SUBMITTED");
        fixture.insertEvidence(projectId, s2, "S2_ARRIVAL_ACCEPTANCE", "SUBMITTED");
        fixture.insertEvidence(projectId, s2, "S2_ARRIVAL_LIST", "SUBMITTED");
        fixture.insertEvidence(projectId, s2, "S2_QUALITY_GUARANTEE", "SUBMITTED");
        for (int i = 0; i < 3; i++) fixture.insertEvidence(projectId, s3, "S3_INSTALL_PHOTO", "SUBMITTED");
        for (int i = 0; i < 3; i++) fixture.insertEvidence(projectId, s3, "S3_SITE_PHOTO", "SUBMITTED");
        fixture.insertEvidence(projectId, s4, "S4_TEST_REPORT", "SUBMITTED");

        long evidenceId = fixture.insertEvidence(projectId, s5Id, "S5_ACCEPTANCE_REPORT", "SUBMITTED");

        StageProgressVO vo1 = stageProgressService.computeStageProgress(projectId);
        StageVO s5_1 = vo1.getStages().stream().filter(s -> "S5".equals(s.getStageCode())).findFirst().orElseThrow();
        assertThat(s5_1.getItemCount()).isEqualTo(1);
        assertThat(s5_1.getCompletedCount()).isEqualTo(1);
        assertThat(s5_1.getItems().stream().anyMatch(i -> Boolean.TRUE.equals(i.getGroupCompleted()))).isTrue();
        assertThat(vo1.getKeyMissing()).doesNotContain(s5GroupDisplayName(vo1));

        fixture.invalidateEvidence(evidenceId);

        StageProgressVO vo2 = stageProgressService.computeStageProgress(projectId);
        StageVO s5_2 = vo2.getStages().stream().filter(s -> "S5".equals(s.getStageCode())).findFirst().orElseThrow();
        assertThat(s5_2.getItemCount()).isEqualTo(1);
        assertThat(s5_2.getCompletedCount()).isEqualTo(0);
        assertThat(s5_2.getHealthStatus()).isEqualTo("NOT_STARTED");
        assertThat(s5_2.getItems()).allMatch(i -> Boolean.FALSE.equals(i.getGroupCompleted()));
        String groupMissing = s5GroupDisplayName(vo2);
        assertThat(vo2.getKeyMissing()).containsExactly(groupMissing);
        assertThat(vo2.getKeyMissing()).singleElement().asString().contains("或");
    }

    private static String s5GroupDisplayName(StageProgressVO vo) {
        return vo.getStages().stream()
                .filter(s -> "S5".equals(s.getStageCode()))
                .flatMap(s -> s.getItems().stream())
                .map(StageItemVO::getGroupDisplayName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();
    }

    /**
     * 【用例 2】required_when：has_procurement true→false → S1 参与项 4→3，overall% 与 keyMissing 立即变化。
     * Arrange: project has_procurement=true；仅 S1 三项有 SUBMITTED 证据（无 S1_PRODUCT_COMPARE）。
     * Act: 第一次 computeStageProgress → 将 has_procurement 改为 false → 第二次 computeStageProgress。
     * Assert: 第一次 S1 itemCount=4、completedCount=3、75%、keyMissing 含比测报告；第二次 S1 itemCount=3、completedCount=3、100%、keyMissing 不含该条。
     */
    @Test
    void requiredWhen_hasProcurementTrueToFalse_s1ParticipatingItemsAndKeyMissingUpdate() {
        long projectId = fixture.createProject(true);
        long s1Id = fixture.getStageIdByCode("S1");
        fixture.insertEvidence(projectId, s1Id, "S1_START_PHOTO", "SUBMITTED");
        fixture.insertEvidence(projectId, s1Id, "S1_START_REPORT", "SUBMITTED");
        fixture.insertEvidence(projectId, s1Id, "S1_IMPL_PLAN", "SUBMITTED");

        StageProgressVO vo1 = stageProgressService.computeStageProgress(projectId);
        StageVO s1_1 = vo1.getStages().stream().filter(s -> "S1".equals(s.getStageCode())).findFirst().orElseThrow();
        assertThat(s1_1.getItemCount()).isEqualTo(4);
        assertThat(s1_1.getCompletedCount()).isEqualTo(3);
        assertThat(s1_1.getCompletionPercent()).isEqualTo(75);
        assertThat(vo1.getKeyMissing()).anyMatch(s -> s != null && s.contains("比测报告"));

        Project p = projectMapper.selectById(projectId);
        p.setHasProcurement(false);
        projectMapper.update(p);

        StageProgressVO vo2 = stageProgressService.computeStageProgress(projectId);
        StageVO s1_2 = vo2.getStages().stream().filter(s -> "S1".equals(s.getStageCode())).findFirst().orElseThrow();
        assertThat(s1_2.getItemCount()).isEqualTo(3);
        assertThat(s1_2.getCompletedCount()).isEqualTo(3);
        assertThat(s1_2.getCompletionPercent()).isEqualTo(100);
        assertThat(vo2.getKeyMissing()).noneMatch(s -> s != null && s.contains("比测报告"));
        assertThat(vo2.getOverallCompletionPercent()).isGreaterThanOrEqualTo(vo1.getOverallCompletionPercent());
    }

    /**
     * 【用例 3】归档门禁：阶段 x==y 但未标记 COMPLETED → canArchive 为 false，archiveBlockReason 指向阶段未完成。
     * Arrange: 创建 project；每阶段参与项均插入足够 SUBMITTED 证据使 x==y；不将 project_stage 置为 COMPLETED。
     * Act: computeStageProgress(projectId)。
     * Assert: 各阶段 completionPercent=100、keyMissing 为空、canArchive=false、archiveBlockReason 非空且为“未标记完成”类文案。
     */
    @Test
    void archiveGate_allStagesXEqualsYButNotMarkedCompleted_canArchiveFalse_reasonStageNotCompleted() {
        long projectId = fixture.createProject(false);
        long s1 = fixture.getStageIdByCode("S1");
        long s2 = fixture.getStageIdByCode("S2");
        long s3 = fixture.getStageIdByCode("S3");
        long s4 = fixture.getStageIdByCode("S4");
        long s5 = fixture.getStageIdByCode("S5");

        fixture.insertEvidence(projectId, s1, "S1_START_PHOTO", "SUBMITTED");
        fixture.insertEvidence(projectId, s1, "S1_START_REPORT", "SUBMITTED");
        fixture.insertEvidence(projectId, s1, "S1_IMPL_PLAN", "SUBMITTED");

        fixture.insertEvidence(projectId, s2, "S2_LOGISTICS_SIGNED", "SUBMITTED");
        for (int i = 0; i < 3; i++) fixture.insertEvidence(projectId, s2, "S2_ARRIVAL_PHOTO", "SUBMITTED");
        fixture.insertEvidence(projectId, s2, "S2_PACKAGE_PHOTO", "SUBMITTED");
        fixture.insertEvidence(projectId, s2, "S2_NAME_PLATE", "SUBMITTED");
        fixture.insertEvidence(projectId, s2, "S2_ARRIVAL_ACCEPTANCE", "SUBMITTED");
        fixture.insertEvidence(projectId, s2, "S2_ARRIVAL_LIST", "SUBMITTED");
        fixture.insertEvidence(projectId, s2, "S2_QUALITY_GUARANTEE", "SUBMITTED");

        for (int i = 0; i < 3; i++) fixture.insertEvidence(projectId, s3, "S3_INSTALL_PHOTO", "SUBMITTED");
        for (int i = 0; i < 3; i++) fixture.insertEvidence(projectId, s3, "S3_SITE_PHOTO", "SUBMITTED");

        fixture.insertEvidence(projectId, s4, "S4_TEST_REPORT", "SUBMITTED");
        fixture.insertEvidence(projectId, s5, "S5_ACCEPTANCE_REPORT", "SUBMITTED");

        StageProgressVO vo = stageProgressService.computeStageProgress(projectId);
        assertThat(vo).isNotNull();
        for (StageVO stage : vo.getStages()) {
            assertThat(stage.getCompletionPercent()).describedAs("stage " + stage.getStageCode()).isEqualTo(100);
        }
        assertThat(vo.getKeyMissing()).isEmpty();
        assertThat(vo.isCanArchive()).isFalse();
        assertThat(vo.getArchiveBlockReason()).isNotBlank();
        assertThat(vo.getArchiveBlockReason()).contains("未标记完成");
    }
}
