package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.BlockedByItemVO;
import com.bwbcomeon.evidence.dto.EvidenceCountRow;
import com.bwbcomeon.evidence.dto.StageCompleteResult;
import com.bwbcomeon.evidence.dto.StageItemVO;
import com.bwbcomeon.evidence.dto.StageProgressVO;
import com.bwbcomeon.evidence.dto.StageVO;
import com.bwbcomeon.evidence.entity.DeliveryStage;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.entity.ProjectStage;
import com.bwbcomeon.evidence.entity.StageEvidenceTemplate;
import com.bwbcomeon.evidence.mapper.DeliveryStageMapper;
import com.bwbcomeon.evidence.mapper.EvidenceItemMapper;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
import com.bwbcomeon.evidence.mapper.ProjectStageMapper;
import com.bwbcomeon.evidence.mapper.StageEvidenceTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 阶段进度与归档门禁：参与计算项过滤、有效证据计数、x/y、overall%、keyMissing、healthStatus、canArchive。
 */
@Service
public class StageProgressService {

    private static final String STATUS_ARCHIVED = "archived";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final int KEY_MISSING_MAX = 10;

    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private DeliveryStageMapper deliveryStageMapper;
    @Autowired
    private StageEvidenceTemplateMapper stageEvidenceTemplateMapper;
    @Autowired
    private ProjectStageMapper projectStageMapper;
    @Autowired
    private EvidenceItemMapper evidenceItemMapper;

    /**
     * 计算项目阶段进度与归档门禁（唯一口径入口）
     */
    /** 单项目进度（如 GET stage-progress、阶段完成、归档）：会懒初始化 project_stage，需写事务 */
    public StageProgressVO computeStageProgress(Long projectId) {
        return computeStageProgress(projectId, true);
    }

    /**
     * 计算阶段进度。
     * @param ensureStages true 时懒初始化 project_stage（INSERT）；false 时仅查询，用于只读事务（如列表批量）
     */
    public StageProgressVO computeStageProgress(Long projectId, boolean ensureStages) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return null;
        }
        if (ensureStages) {
            ensureProjectStages(projectId);
        }
        boolean hasProcurement = Boolean.TRUE.equals(project.getHasProcurement());

        List<DeliveryStage> stages = deliveryStageMapper.selectAll();
        List<StageEvidenceTemplate> allTemplates = stageEvidenceTemplateMapper.selectAll();
        List<ProjectStage> projectStages = projectStageMapper.selectByProjectId(projectId);
        Map<Long, ProjectStage> stageIdToProjectStage = projectStages.stream()
                .collect(Collectors.toMap(ProjectStage::getStageId, ps -> ps, (a, b) -> a));

        List<EvidenceCountRow> countRows = evidenceItemMapper.countValidEvidenceByProjectId(projectId);
        Map<String, Long> countKeyToCnt = new HashMap<>();
        for (EvidenceCountRow r : countRows) {
            countKeyToCnt.put(key(r.getStageId(), r.getEvidenceTypeCode()), r.getCount());
        }

        List<StageVO> stageVOList = new ArrayList<>();
        int totalY = 0;
        int totalX = 0;
        List<String> keyMissing = new ArrayList<>();
        List<BlockedByItemVO> blockedByRequiredItems = new ArrayList<>();

        for (DeliveryStage stage : stages) {
            List<StageEvidenceTemplate> rows = allTemplates.stream()
                    .filter(t -> t.getStageId().equals(stage.getId()))
                    .filter(t -> participating(t, hasProcurement))
                    .sorted(Comparator.comparingInt(t -> t.getSortOrder() != null ? t.getSortOrder() : 0))
                    .toList();

            if (rows.isEmpty()) {
                continue;
            }

            List<StageItemVO> items = new ArrayList<>();
            Set<String> groupIdsCounted = new HashSet<>();
            Set<String> groupIdsMissingAdded = new HashSet<>();
            int stageY = 0;
            int stageX = 0;
            Map<String, List<StageEvidenceTemplate>> byGroup = rows.stream()
                    .collect(Collectors.groupingBy(t -> t.getRuleGroup() != null && !t.getRuleGroup().isBlank() ? t.getRuleGroup() : "standalone_" + t.getId()));

            for (StageEvidenceTemplate row : rows) {
                long currentCount = countKeyToCnt.getOrDefault(key(row.getStageId(), row.getEvidenceTypeCode()), 0L);
                boolean rowCompleted = currentCount >= (row.getMinCount() != null ? row.getMinCount() : 1);

                StageItemVO item = new StageItemVO();
                item.setEvidenceTypeCode(row.getEvidenceTypeCode());
                item.setDisplayName(row.getDisplayName());
                item.setRequired(Boolean.TRUE.equals(row.getIsRequired()));
                item.setMinCount(row.getMinCount() != null ? row.getMinCount() : 1);
                item.setCurrentCount((int) currentCount);
                item.setCompleted(rowCompleted);
                item.setRuleGroup(row.getRuleGroup());
                item.setSortOrder(row.getSortOrder());

                if (row.getRuleGroup() != null && !row.getRuleGroup().isBlank()) {
                    String g = row.getRuleGroup();
                    List<StageEvidenceTemplate> groupRows = byGroup.get(g);
                    int satisfied = (int) groupRows.stream().filter(tr -> {
                        long c = countKeyToCnt.getOrDefault(key(tr.getStageId(), tr.getEvidenceTypeCode()), 0L);
                        return c >= (tr.getMinCount() != null ? tr.getMinCount() : 1);
                    }).count();
                    int required = groupRows.get(0).getGroupRequiredCount() != null ? groupRows.get(0).getGroupRequiredCount() : 1;
                    boolean groupCompleted = satisfied >= required;
                    item.setGroupCompleted(groupCompleted);
                    String groupDisplayName = groupRows.stream().map(StageEvidenceTemplate::getDisplayName).collect(Collectors.joining("或"));
                    item.setGroupDisplayName(groupDisplayName);

                    if (!groupIdsCounted.contains(g)) {
                        groupIdsCounted.add(g);
                        stageY += 1;
                        if (groupCompleted) stageX += 1;
                    }
                    boolean groupRequired = groupRows.stream().anyMatch(tr -> Boolean.TRUE.equals(tr.getIsRequired()));
                    if (groupRequired && !groupCompleted && !groupIdsMissingAdded.contains(g)) {
                        groupIdsMissingAdded.add(g);
                        if (keyMissing.size() < KEY_MISSING_MAX) keyMissing.add(groupDisplayName);
                        BlockedByItemVO blocked = new BlockedByItemVO();
                        blocked.setStageCode(stage.getCode());
                        blocked.setEvidenceTypeCode(null);
                        blocked.setDisplayName(groupDisplayName);
                        blocked.setShortfall(1);
                        blockedByRequiredItems.add(blocked);
                    }
                } else {
                    stageY += 1;
                    if (rowCompleted) stageX += 1;
                    item.setGroupCompleted(null);
                    item.setGroupDisplayName(null);
                    if (Boolean.TRUE.equals(row.getIsRequired()) && !rowCompleted && !keyMissing.contains(row.getDisplayName())) {
                        if (keyMissing.size() < KEY_MISSING_MAX) keyMissing.add(row.getDisplayName());
                        int minC = row.getMinCount() != null ? row.getMinCount() : 1;
                        int shortfall = Math.max(0, minC - (int) currentCount);
                        BlockedByItemVO blocked = new BlockedByItemVO();
                        blocked.setStageCode(stage.getCode());
                        blocked.setEvidenceTypeCode(row.getEvidenceTypeCode());
                        blocked.setDisplayName(row.getDisplayName());
                        blocked.setShortfall(shortfall);
                        blockedByRequiredItems.add(blocked);
                    }
                }
                items.add(item);
            }

            totalY += stageY;
            totalX += stageX;

            int completionPercent = stageY == 0 ? 100 : Math.round(stageX * 100f / stageY);
            String healthStatus = (stageX == stageY && stageY > 0) ? "COMPLETE" : (stageX == 0 && stageY > 0) ? "NOT_STARTED" : "PARTIAL";
            ProjectStage ps = stageIdToProjectStage.get(stage.getId());
            boolean stageCompleted = ps != null && STATUS_COMPLETED.equals(ps.getStatus());
            boolean canComplete = (stageX == stageY);

            StageVO vo = new StageVO();
            vo.setStageId(stage.getId());
            vo.setStageCode(stage.getCode());
            vo.setStageName(stage.getName());
            vo.setStageDescription(stage.getDescription());
            vo.setItemCount(stageY);
            vo.setCompletedCount(stageX);
            vo.setCompletionPercent(completionPercent);
            vo.setHealthStatus(healthStatus);
            vo.setStageCompleted(stageCompleted);
            vo.setCanComplete(canComplete);
            vo.setItems(items);
            stageVOList.add(vo);
        }

        int overallPercent = totalY == 0 ? 100 : Math.round(totalX * 100f / totalY);
        boolean allStages100 = stageVOList.stream().allMatch(s -> s.getCompletedCount() == s.getItemCount() && s.getItemCount() > 0);
        boolean allStagesMarkedCompleted = stageVOList.stream().allMatch(StageVO::isStageCompleted);
        boolean notArchived = !STATUS_ARCHIVED.equals(project.getStatus());
        boolean canArchive = notArchived && allStages100 && allStagesMarkedCompleted && keyMissing.isEmpty();

        String archiveBlockReason = null;
        List<String> blockedByStages = new ArrayList<>();
        if (!canArchive && notArchived) {
            if (!keyMissing.isEmpty()) {
                archiveBlockReason = "缺少关键证据，不可归档：" + String.join("、", keyMissing.subList(0, Math.min(3, keyMissing.size())));
            } else if (!allStagesMarkedCompleted) {
                archiveBlockReason = "存在未标记完成的阶段，不可归档";
            } else if (!allStages100) {
                archiveBlockReason = "存在阶段未满足完成条件，不可归档";
            }
            for (StageVO s : stageVOList) {
                if (!s.isStageCompleted() || s.getCompletedCount() < s.getItemCount()) {
                    blockedByStages.add(s.getStageCode());
                }
            }
        }

        StageProgressVO result = new StageProgressVO();
        result.setOverallCompletionPercent(overallPercent);
        result.setKeyMissing(keyMissing);
        result.setCanArchive(canArchive);
        result.setArchiveBlockReason(archiveBlockReason);
        result.setStages(stageVOList);
        result.setProjectName(project.getName());
        result.setProjectStatus(project.getStatus());
        result.setHasProcurement(hasProcurement);
        result.setBlockedByStages(blockedByStages);
        result.setBlockedByRequiredItems(blockedByRequiredItems);
        return result;
    }

    /** 确保项目存在 5 条 project_stage 记录（懒初始化） */
    public void ensureProjectStages(Long projectId) {
        List<ProjectStage> existing = projectStageMapper.selectByProjectId(projectId);
        if (!existing.isEmpty()) {
            return;
        }
        List<DeliveryStage> stages = deliveryStageMapper.selectAll();
        for (DeliveryStage ds : stages) {
            ProjectStage ps = new ProjectStage();
            ps.setProjectId(projectId);
            ps.setStageId(ds.getId());
            ps.setStatus("NOT_STARTED");
            projectStageMapper.insert(ps);
        }
    }

    /**
     * 标记阶段完成：门禁通过则更新 project_stage 为 COMPLETED，否则返回缺失项
     */
    public StageCompleteResult completeStage(Long projectId, String stageCode) {
        ensureProjectStages(projectId);
        DeliveryStage stage = deliveryStageMapper.selectByCode(stageCode);
        if (stage == null) {
            return StageCompleteResult.fail("阶段不存在", List.of());
        }
        StageProgressVO progress = computeStageProgress(projectId);
        if (progress == null) {
            return StageCompleteResult.fail("项目不存在", List.of());
        }
        StageVO stageVo = progress.getStages().stream().filter(s -> stageCode.equals(s.getStageCode())).findFirst().orElse(null);
        if (stageVo == null) {
            return StageCompleteResult.fail("阶段不在参与计算范围内", List.of());
        }
        if (stageVo.getCompletedCount() != stageVo.getItemCount()) {
            List<BlockedByItemVO> missing = new ArrayList<>();
            Set<String> addedGroupDisplay = new HashSet<>();
            for (StageItemVO item : stageVo.getItems()) {
                if (item.getRuleGroup() != null && !Boolean.TRUE.equals(item.getGroupCompleted())) {
                    String gdn = item.getGroupDisplayName();
                    if (gdn != null && !addedGroupDisplay.contains(gdn)) {
                        addedGroupDisplay.add(gdn);
                        BlockedByItemVO b = new BlockedByItemVO();
                        b.setStageCode(stageCode);
                        b.setEvidenceTypeCode(null);
                        b.setDisplayName(gdn);
                        b.setShortfall(1);
                        missing.add(b);
                    }
                } else if (item.isRequired() && !item.isCompleted()) {
                    BlockedByItemVO b = new BlockedByItemVO();
                    b.setStageCode(stageCode);
                    b.setEvidenceTypeCode(item.getEvidenceTypeCode());
                    b.setDisplayName(item.getDisplayName());
                    b.setShortfall(Math.max(0, item.getMinCount() - item.getCurrentCount()));
                    missing.add(b);
                }
            }
            return StageCompleteResult.fail("阶段未满足完成条件", missing);
        }
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        projectStageMapper.updateStatus(projectId, stage.getId(), STATUS_COMPLETED, now);
        return StageCompleteResult.ok();
    }

    /**
     * 批量计算阶段进度（用于列表扩展，避免 N+1）。
     * 在只读事务中调用，不执行 ensureProjectStages（不 INSERT project_stage）。
     */
    public Map<Long, StageProgressVO> computeStageProgressBatch(List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, StageProgressVO> map = new HashMap<>();
        for (Long id : projectIds) {
            StageProgressVO vo = computeStageProgress(id, false);
            if (vo != null) {
                map.put(id, vo);
            }
        }
        return map;
    }

    private static boolean participating(StageEvidenceTemplate t, boolean hasProcurement) {
        if (t.getRequiredWhen() == null || t.getRequiredWhen().isBlank()) {
            return true;
        }
        if ("HAS_PROCUREMENT".equals(t.getRequiredWhen())) {
            return hasProcurement;
        }
        return true;
    }

    private static String key(Long stageId, String evidenceTypeCode) {
        return stageId + ":" + evidenceTypeCode;
    }
}
