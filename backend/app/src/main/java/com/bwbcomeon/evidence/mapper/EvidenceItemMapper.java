package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.dto.EvidenceCountRow;
import com.bwbcomeon.evidence.entity.EvidenceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 证据元数据表 Mapper
 */
@Mapper
public interface EvidenceItemMapper {
    /**
     * 根据ID查询证据
     */
    EvidenceItem selectById(@Param("id") Long id);

    /**
     * 根据项目ID查询证据列表
     */
    List<EvidenceItem> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 根据项目ID和证据状态查询证据列表
     */
    List<EvidenceItem> selectByProjectIdAndEvidenceStatus(@Param("projectId") Long projectId, @Param("evidenceStatus") String evidenceStatus);

    /**
     * 根据创建人查询证据列表
     */
    List<EvidenceItem> selectByCreatedBy(@Param("createdByUserId") Long createdByUserId);

    /**
     * 查询所有证据
     */
    List<EvidenceItem> selectAll();

    /**
     * 插入证据
     */
    int insert(EvidenceItem evidenceItem);

    /**
     * 更新证据
     */
    int update(EvidenceItem evidenceItem);

    /**
     * 根据ID删除证据
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据项目ID和条件查询证据列表（支持名称模糊匹配、证据状态、证据类型、文件类型过滤）
     * @param projectId 项目ID
     * @param nameLike 证据名称模糊匹配（可选）
     * @param evidenceStatus 证据状态（可选，DRAFT/SUBMITTED/ARCHIVED/INVALID）
     * @param evidenceTypeCode 证据类型编码（可选）
     * @param contentType 文件类型（MIME类型，可选）
     * @return 证据列表
     */
    List<EvidenceItem> selectByProjectIdWithFilters(
            @Param("projectId") Long projectId,
            @Param("nameLike") String nameLike,
            @Param("evidenceStatus") String evidenceStatus,
            @Param("evidenceTypeCode") String evidenceTypeCode,
            @Param("contentType") String contentType
    );

    /**
     * 分页查询证据（仅限可见项目内，支持 projectId/status/uploader/recentDays/fileCategory）
     * @param projectIds 可见项目ID列表（非空）
     * @param projectId 按项目筛选（可选，与 projectIds 取交集）
     * @param status 状态（可选；不传时默认排除 invalid）
     * @param createdByUserId 上传人 sys_user.id（可选，uploader=me 时传入）
     * @param createdAfter 创建时间下限（可选，recentDays 时传入）
     * @param fileCategory 文件大类：image/document/video（可选，映射到 content_type）
     * @param nameLike 标题模糊（可选）
     * @param offset 偏移
     * @param limit 条数
     */
    List<EvidenceItem> selectPageWithFilters(
            @Param("projectIds") List<Long> projectIds,
            @Param("projectId") Long projectId,
            @Param("status") String status,
            @Param("createdByUserId") Long createdByUserId,
            @Param("createdAfter") java.time.OffsetDateTime createdAfter,
            @Param("fileCategory") String fileCategory,
            @Param("nameLike") String nameLike,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    long countPageWithFilters(
            @Param("projectIds") List<Long> projectIds,
            @Param("projectId") Long projectId,
            @Param("status") String status,
            @Param("createdByUserId") Long createdByUserId,
            @Param("createdAfter") java.time.OffsetDateTime createdAfter,
            @Param("fileCategory") String fileCategory,
            @Param("nameLike") String nameLike
    );

    /**
     * 更新证据生命周期状态（乐观约束：仅当前状态匹配时更新）
     * @return 更新行数，0 表示状态已变更或不存在
     */
    int updateEvidenceStatus(
            @Param("id") Long id,
            @Param("evidenceStatus") String evidenceStatus,
            @Param("archivedTime") java.time.OffsetDateTime archivedTime,
            @Param("invalidTime") java.time.OffsetDateTime invalidTime,
            @Param("expectedCurrentStatus") String expectedCurrentStatus
    );

    /**
     * 作废证据：更新状态并写入 invalid_reason / invalid_by / invalid_at（时间优先 invalid_at）
     * 状态保护：WHERE id=? AND evidence_status&lt;&gt;'INVALID'
     * @return 更新行数，0 表示已作废或状态不允许
     */
    int updateEvidenceInvalidate(
            @Param("id") Long id,
            @Param("evidenceStatus") String evidenceStatus,
            @Param("invalidTime") java.time.OffsetDateTime invalidTime,
            @Param("invalidReason") String invalidReason,
            @Param("invalidByUserId") Long invalidByUserId,
            @Param("invalidAt") java.time.OffsetDateTime invalidAt
    );

    /**
     * 门禁口径：按项目统计有效证据数（仅 SUBMITTED + ARCHIVED，不含 DRAFT/INVALID），
     * 按 (stage_id, evidence_type_code) 分组，用于阶段完成判断、归档门禁、keyMissing、completionPercent
     */
    List<EvidenceCountRow> countValidEvidenceByProjectId(@Param("projectId") Long projectId);

    /**
     * 展示口径：按项目统计已上传证据数（DRAFT + SUBMITTED + ARCHIVED，不含 INVALID），
     * 按 (stage_id, evidence_type_code) 分组，仅用于 UI 展示"已上传数量"，不影响门禁判断
     */
    List<EvidenceCountRow> countUploadedEvidenceByProjectId(@Param("projectId") Long projectId);

    /**
     * 按项目+阶段+证据类型查询证据列表（用于按阶段+模板项证据列表接口）
     */
    List<EvidenceItem> selectByProjectIdAndStageIdAndEvidenceTypeCode(
            @Param("projectId") Long projectId,
            @Param("stageId") Long stageId,
            @Param("evidenceTypeCode") String evidenceTypeCode
    );
}
