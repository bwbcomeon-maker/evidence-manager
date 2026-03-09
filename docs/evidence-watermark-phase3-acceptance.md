# 图片水印功能 - 第三阶段联调验收与收口

## 验收范围

基于第一、二轮已完成的改造，对本功能进行联调验证与验收清单输出，确认是否达到可并入主线、可进入整体测试的状态。**不做大改造、不新增复杂设计。**

---

## 一、测试场景与验收记录

### 场景 1：jpg / jpeg / png / webp 上传

| 项目 | 内容 |
|------|------|
| **测试场景** | 分别上传 jpg、jpeg、png、webp 四种图片，验证上传成功且水印图生成与展示 |
| **操作步骤** | 1. 登录系统，进入某项目证据上传入口<br>2. 依次选择 jpg、jpeg、png、webp 格式图片各一张上传<br>3. 查看列表/详情中该证据的预览图 |
| **预期结果** | 四种格式均上传成功；证据记录存在；图片类型均生成水印图（或 webp 在无插件时走降级）；预览默认显示水印图（有则显示，无则回退原图） |
| **实际结果** | jpg/jpeg/png：代码路径完整，水印生成与存储逻辑正确；WebP 见「重点检查」结论 |
| **是否通过** | jpg/jpeg/png 通过；WebP 依赖运行环境，见下文 |

---

### 场景 2：历史图片无水印回退

| 项目 | 内容 |
|------|------|
| **测试场景** | 对无 watermarked_file_path 的历史图片证据进行预览/下载 |
| **操作步骤** | 1. 使用改造前已存在的图片证据，或 watermarked_file_path 为空的记录<br>2. 在列表/详情中点击预览，或调用下载接口（不传 variant 或 variant=WATERMARKED） |
| **预期结果** | 不报错；返回原图内容；响应正常 |
| **实际结果** | 代码逻辑：当 watermarked_file_path 为空或文件不存在时，resolveDownloadFilePath 返回原图路径，下载/预览均回退到原图 |
| **是否通过** | 通过（建议在预发/测试库用一条历史数据再跑一次） |

---

### 场景 3：非图片文件不受影响

| 项目 | 内容 |
|------|------|
| **测试场景** | 上传 PDF、Word 等非图片文件，验证不触发水印逻辑且预览/下载正常 |
| **操作步骤** | 1. 上传 PDF、Word 等非图片证据<br>2. 预览、下载该证据 |
| **预期结果** | 上传成功；不生成水印图；evidence_version 无 watermarked_file_path；预览/下载为原文件 |
| **实际结果** | isSupportedImage 对非 image/* 及非 jpg/jpeg/png/webp 扩展名返回 false，上传流程不进入水印分支；下载时非图片直接走原图路径 |
| **是否通过** | 通过 |

---

### 场景 4：preview=true 预览行为

| 项目 | 内容 |
|------|------|
| **测试场景** | 使用 preview=true 请求版本文件，验证为内联展示且内容为水印优先 |
| **操作步骤** | 1. 获取某图片证据的 versionId<br>2. 请求 GET /api/evidence/versions/{versionId}/download?preview=true<br>3. 查看响应头与响应体 |
| **预期结果** | Content-Disposition: inline；图片类型时返回水印图（若有），否则原图；非图片为原文件 |
| **实际结果** | Controller 根据 preview=true 设置 inline；文件内容与 variant 解析逻辑与下载一致，图片默认 WATERMARKED |
| **是否通过** | 通过 |

---

### 场景 5：默认下载行为

| 项目 | 内容 |
|------|------|
| **测试场景** | 不传 variant 或传 variant=WATERMARKED 的下载请求 |
| **操作步骤** | GET /api/evidence/versions/{versionId}/download（可不带 preview 或 preview=false） |
| **预期结果** | Content-Disposition: attachment；图片类型返回水印图（若有则用，无则原图）；非图片为原文件 |
| **实际结果** | normalizeVariant 默认 WATERMARKED；resolveDownloadFilePath 对图片优先水印路径，历史无水印则原图 |
| **是否通过** | 通过 |

---

### 场景 6：variant=ORIGINAL 在 SYSTEM_ADMIN / PMO 下可访问

| 项目 | 内容 |
|------|------|
| **测试场景** | 配置 evidence.image.original-access-enabled=true 时，SYSTEM_ADMIN 或 PMO 用户请求原图 |
| **操作步骤** | 1. 配置项设为 true<br>2. 使用 SYSTEM_ADMIN 或 PMO 账号登录<br>3. 请求 GET /api/evidence/versions/{versionId}/download?variant=ORIGINAL |
| **预期结果** | 200；返回原图文件内容 |
| **实际结果** | resolveDownloadFilePath 在 variant=ORIGINAL 时校验配置与 roleCode，SYSTEM_ADMIN/PMO 通过后返回原图路径 |
| **是否通过** | 通过（需在真实环境用对应角色账号验证一次） |

---

### 场景 7：variant=ORIGINAL 在普通角色下返回 403

| 项目 | 内容 |
|------|------|
| **测试场景** | 配置开启时，非 SYSTEM_ADMIN/PMO 用户请求 variant=ORIGINAL |
| **操作步骤** | 1. evidence.image.original-access-enabled=true<br>2. 使用普通项目成员等账号<br>3. 请求 GET /api/evidence/versions/{versionId}/download?variant=ORIGINAL |
| **预期结果** | 403；明确提示仅系统管理员或 PMO 可访问原图 |
| **实际结果** | roleAllowed 为 false 时抛出 BusinessException(403, "仅系统管理员或PMO可访问原图") |
| **是否通过** | 通过 |

---

### 场景 8：退回后替换证据的新旧隔离

| 项目 | 内容 |
|------|------|
| **测试场景** | 对已退回的图片证据执行「替换」，上传新图，验证新证据为新水印、旧证据仍为旧版本 |
| **操作步骤** | 1. 对某条已退回的图片证据点击替换，选择新图片上传<br>2. 确认生成一条新证据（新 ID）<br>3. 查看新证据预览为带新水印的新图；旧证据状态为作废，其预览仍为旧图 |
| **预期结果** | 新证据 version_no=1、新原图+新水印；旧证据保留原版本与文件，状态 INVALID；二者互不污染 |
| **实际结果** | 前端为「新证据上传 + 旧证据作废」，后端上传新证据时走统一 uploadEvidence，生成新水印并写入新版本；旧证据仅作废，不删版本与文件 |
| **是否通过** | 通过 |

---

### 场景 9：DRAFT 删除的文件清理

| 项目 | 内容 |
|------|------|
| **测试场景** | 删除一条草稿状态的图片证据，验证磁盘原图与水印图均被删除 |
| **操作步骤** | 1. 创建并上传一条草稿图片证据（确认存在原图与水印图文件）<br>2. 在列表/详情中对该证据执行「删除」<br>3. 检查 data/uploads/{projectId}/{evidenceId}/ 下对应文件是否已不存在 |
| **预期结果** | 证据记录删除；该证据下所有 version 记录删除；原图与水印图文件物理删除 |
| **实际结果** | deleteEvidence 仅允许 DRAFT；遍历 versions 删除 file_path 与 watermarked_file_path 对应文件并 deleteById；最后删除 evidence_item |
| **是否通过** | 通过 |

---

### 场景 10：SUBMITTED 作废的文件保留

| 项目 | 内容 |
|------|------|
| **测试场景** | 对已提交的图片证据执行「作废」，验证磁盘文件未删除 |
| **操作步骤** | 1. 对一条已提交的图片证据执行作废并填写作废原因<br>2. 检查 evidence_version 记录仍在；data/uploads 下该证据的原图与水印图文件仍存在 |
| **预期结果** | 证据状态变为 INVALID；版本记录与磁盘文件均保留 |
| **实际结果** | invalidateEvidence 仅更新 evidence_item 状态与作废信息，不删 version、不删文件；注释已明确「作废后不删除磁盘文件」 |
| **是否通过** | 通过 |

---

## 二、重点检查

### 1. 水印图实际格式与响应 Content-Type 是否一致

| 项目 | 结论 |
|------|------|
| **当前实现** | 下载/预览返回的 Content-Type 取自 `version.getContentType()`（即**原图**的 MIME 类型），与本次请求实际返回的文件路径（原图或水印图）无关。 |
| **一致性** | 当水印图与原图格式相同时（如原图 jpg→水印 jpg、原图 png→水印 png），一致。当水印落盘格式与原图不同时（例如原图为 webp、水印 fallback 为 png），**不一致**：响应仍为 image/webp，实体为 image/png。 |
| **影响** | 部分浏览器或客户端可能依赖 Content-Type 解析；若严格需一致，需在返回水印图时按水印实际格式设置 Content-Type（例如按 watermarked_filename 扩展名或新增 watermarked_content_type 字段）。 |

### 2. WebP 在当前运行环境是否可生成水印

| 项目 | 结论 |
|------|------|
| **当前环境** | 项目 pom.xml 未引入 WebP 的 ImageIO 插件（如 imageio-webp）。JDK 标准 ImageIO 不包含 WebP 编解码。 |
| **实际行为** | 上传 webp 时，isSupportedImage 为 true 会进入水印分支；ImageIO.read(webpBytes) 无法解码 WebP，会抛异常或返回 null，被 catch 后打 warn 日志，**水印生成失败但不阻断上传**，仅保存原图，无 watermarked_file_path。 |
| **结论** | **当前运行环境下 WebP 无法生成水印**；上传的 WebP 仍可正常存储与预览/下载（以原图形式）。若需 WebP 水印，需后续引入 WebP ImageIO 依赖并验证。 |

---

## 三、风险点与建议

| 风险点 | 建议 |
|--------|------|
| 水印格式与 Content-Type 可能不一致 | 返回水印图时按实际文件格式设置 Content-Type（见下文「可后续迭代」）。 |
| WebP 无水印 | 若业务要求 WebP 也带水印，需引入并验证 WebP ImageIO 插件；当前为降级策略，不影响上传与查看。 |
| 历史数据未批量补水印 | 保持现状即可；预览/下载已回退原图，无功能缺陷。 |
| 原图访问依赖配置与角色 | 上线前确认 evidence.image.original-access-enabled 与角色配置符合安全要求。 |

---

## 四、上线前必须处理的问题

1. **无**：当前实现满足「原图保留、水印派生、预览默认水印、历史回退、权限与删除/作废策略」等既定需求；未发现阻塞主线的致命缺陷。
2. **建议在预发/测试环境执行**：上述 10 个场景各跑一遍（含不同角色、配置开闭），并抽查一条历史无水印数据做回退验证。

---

## 五、可后续迭代优化的问题

1. **Content-Type 与实体一致**：返回水印图时，根据 `watermarked_filename` 扩展名或新增 `watermarked_content_type` 字段，设置响应的 Content-Type 为水印实际 MIME，避免 webp→png 等 fallback 时不一致。
2. **WebP 水印支持**：如需 WebP 也生成水印，引入 imageio-webp（或等价）依赖，在目标环境验证读/写 WebP 后，再开放或保持当前降级策略。
3. **水印图 Content-Type 持久化**：若希望下载接口完全依赖数据库而不依赖文件名解析，可在 evidence_version 增加 watermarked_content_type，在上传生成水印时写入。

---

## 六、验收结论

- **联调与代码审查结论**：上述场景在实现上均符合设计；删除/作废策略、原图权限、替换证据新旧隔离、历史回退等逻辑正确。
- **建议**：在测试/预发环境按本文档完成一轮手工或自动化联调（含 WebP 与 Content-Type 的实际情况记录），并确认配置与角色符合上线要求。

**最终结论：可进入主线整体测试。**  
建议在进入整体测试前，按本文档在测试/预发环境完成一轮联调验证（含历史数据回退、原图 403、替换证据新旧隔离及 WebP/Content-Type 的实际情况记录）；若产品后续要求 WebP 必须带水印或响应 Content-Type 与实体严格一致，再在迭代中处理。
