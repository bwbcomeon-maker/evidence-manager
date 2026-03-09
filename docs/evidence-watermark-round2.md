# 证据图片水印功能 - 第二轮加固说明

## 1. 预览与下载语义说明

- **接口**：`GET /api/evidence/versions/{versionId}/download`（预览与下载共用）
- **预览**：传 `preview=true`，响应头 `Content-Disposition: inline`，供页面内嵌展示；图片默认返回水印图（无水印则回退原图），非图片返回原文件。
- **下载**：不传或 `preview=false`，响应头 `Content-Disposition: attachment`，触发浏览器下载；文件内容与预览一致（图片默认水印优先）。
- **原图**：传 `variant=ORIGINAL` 且满足「系统开关 + 角色」时返回原图；否则 403。详见下文原图访问权限。

## 2. 原图访问权限控制说明

- **系统开关**：`evidence.image.original-access-enabled`（默认 false）。为 false 时，任何角色请求 `variant=ORIGINAL` 均返回 403「当前环境未开放原图下载」。
- **角色控制**：在开关为 true 的前提下，仅 **SYSTEM_ADMIN**、**PMO** 可访问 `variant=ORIGINAL`；其他角色返回 403「仅系统管理员或PMO可访问原图」。
- 无权限时不静默降级，统一 403。

## 3. 删除/作废文件清理策略说明

- **删除（deleteEvidence）**：仅 **草稿（DRAFT）** 可物理删除。会删除 `evidence_item`、该证据下全部 `evidence_version` 记录，以及磁盘上的 **原图** 与 **水印图** 文件。已提交/已归档证据不可物理删除，只能作废。
- **作废（invalidateEvidence）**：仅 **已提交（SUBMITTED）** 可作废。只更新证据状态为 INVALID 并记录作废原因/人/时间，**不删除** 任何版本记录与磁盘文件（原图与水印图保留），便于审计与追溯。

## 4. 替换退回证据链路验证

- **前端行为**（`handleReplaceRejectedEvidence`）：先调用上传接口生成 **一条新证据**（新 evidenceId，version_no=1），再对 **旧证据** 调用作废接口。
- **后端**：上传新证据时走统一 `uploadEvidence`，会为图片生成新水印并写入新版本的 `watermarked_file_path` / `watermarked_filename`；旧证据作废后保留其版本与文件，不修改。因此：
  - 新证据 = 新版本 1 + 新原图 + 新水印图；
  - 旧证据 = 保留原版本与文件，状态变为 INVALID。
- **验证点**：
  1. 对某条已退回的图片证据执行「替换」并选择新图片上传。
  2. 列表中应出现一条新证据（新 ID），预览/下载为新图且带新水印。
  3. 旧证据状态为「作废」，其预览仍为旧水印图（或旧原图），不被新证据污染。

## 5. 回归测试要点

- **图片格式**：jpg、jpeg、png、webp 各上传一次，确认：上传成功；该证据存在水印图；预览默认显示水印图；历史数据（无水印字段或旧数据）回退显示原图不报错。
- **非图片**：上传 PDF、Word 等，确认：上传成功；不生成水印；预览/下载为原文件；行为与改造前一致。
- **原图访问**：配置关闭时 `variant=ORIGINAL` 返回 403；配置开启且非 SYSTEM_ADMIN/PMO 时 403；配置开启且 SYSTEM_ADMIN 或 PMO 时可正常返回原图。

## 6. 剩余风险点

- **Content-Type 与实体一致**：当前下载接口返回的 Content-Type 取自证据版本的 `content_type`（原图类型）。若水印输出格式与原图不同（如原图为 webp 而水印落盘为 png），响应 Content-Type 可能与实际字节不一致；仅影响极少数格式组合，后续可考虑在版本表增加 `watermarked_content_type` 并在返回水印时使用。
- **WebP 编解码**：部分运行环境未自带 WebP 的 ImageIO 插件，上传 webp 时可能水印生成失败并走“水印失败不阻断上传”的降级，需在目标环境验证。
