# 证据列表接口说明（给前端用）

## GET /api/evidence

全局证据分页列表，仅返回**当前登录用户可见项目**内的证据；默认**不返回作废证据**，除非显式传 `status=VOIDED`。

### 请求方式

`GET`

### 请求头

需携带登录态（Session Cookie），否则返回 401。

### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 1 | 当前页码（从 1 开始） |
| pageSize | int | 否 | 10 | 每页条数（建议不超过 100） |
| projectId | long | 否 | - | 按项目筛选，只查指定项目下的证据 |
| status | string | 否 | - | 状态筛选。传 `VOIDED` 时只查作废证据；不传时默认不返回作废证据 |
| uploader | string | 否 | - | 上传人：传 `me` 表示「我上传的证据」（后端会转为当前用户）；传具体用户 ID 时按该上传人筛选（若后端支持） |
| recentDays | int | 否 | - | 最近 N 天内上传：按 `upload_time >= now - recentDays` 筛选，例如 `7` 表示最近 7 天 |
| fileCategory | string | 否 | - | 按文件类型大类：`image` / `document` / `video`，见下方映射规则 |
| nameLike | string | 否 | - | 证据名称模糊匹配 |

### fileCategory 映射规则

- **image**：jpg, jpeg, png, gif, webp  
- **video**：mp4, mov, avi, mkv  
- **document**：pdf, doc, docx, xls, xlsx, ppt, pptx, txt, zip, rar  

后端优先按 MIME/content_type 匹配；无则按文件扩展名判断。

### 响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 100,
    "records": [ /* EvidenceListItemVO 数组 */ ],
    "page": 1,
    "pageSize": 10
  }
}
```

- **code**：0 表示成功，非 0 表示失败（如 401 未登录）。  
- **message**：提示信息。  
- **data**：分页结果。  
  - **total**：总条数。  
  - **records**：当前页证据列表。  
  - **page**：当前页码。  
  - **pageSize**：每页条数。

### records 中单条结构（EvidenceListItemVO）

与「按项目查证据」接口的列表项一致，包含但不限于：

- evidenceId, projectId, title, bizType, contentType, status  
- createdBy（上传人 UUID）, createdAt, updatedAt  
- latestVersion：{ versionId, versionNo, originalFilename, filePath, fileSize, createdAt }

### 前端四个入口对应参数示例

1. **我上传的证据**：`uploader=me`  
2. **最近上传的证据**：`recentDays=7`（或 30 等）  
3. **作废证据**：`status=VOIDED`  
4. **按文件类型查看**：`fileCategory=image` 或 `document` 或 `video`  

可组合使用，例如：我上传的 + 最近 7 天 + 仅图片：`uploader=me&recentDays=7&fileCategory=image`。
