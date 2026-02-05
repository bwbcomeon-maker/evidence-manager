package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.EvidenceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

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
     * 根据项目ID和状态查询证据列表
     */
    List<EvidenceItem> selectByProjectIdAndStatus(@Param("projectId") Long projectId, @Param("status") String status);

    /**
     * 根据创建人查询证据列表
     */
    List<EvidenceItem> selectByCreatedBy(@Param("createdBy") UUID createdBy);

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
     * 根据项目ID和条件查询证据列表（支持名称模糊匹配、状态过滤、业务类型过滤、文件类型过滤）
     * @param projectId 项目ID
     * @param nameLike 证据名称模糊匹配（可选）
     * @param status 证据状态（可选）
     * @param bizType 业务证据类型（可选，如PLAN/REPORT/MINUTES/TEST/ACCEPTANCE/OTHER）
     * @param contentType 文件类型（MIME类型，可选，如application/pdf）
     * @return 证据列表
     */
    List<EvidenceItem> selectByProjectIdWithFilters(
            @Param("projectId") Long projectId,
            @Param("nameLike") String nameLike,
            @Param("status") String status,
            @Param("bizType") String bizType,
            @Param("contentType") String contentType
    );

    /**
     * 分页查询证据（仅限可见项目内，支持 projectId/status/uploader/recentDays/fileCategory）
     * @param projectIds 可见项目ID列表（非空）
     * @param projectId 按项目筛选（可选，与 projectIds 取交集）
     * @param status 状态（可选；不传时默认排除 invalid）
     * @param createdBy 上传人 UUID（可选，uploader=me 时传入）
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
            @Param("createdBy") UUID createdBy,
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
            @Param("createdBy") UUID createdBy,
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
            @Param("invalidBy") java.util.UUID invalidBy,
            @Param("invalidAt") java.time.OffsetDateTime invalidAt
    );
}
