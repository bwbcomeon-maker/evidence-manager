# 项目交付证据管理系统
## 数据库设计与业务场景说明（MVP 版）

---

## 1. 文档概述

### 1.1 编制目的

本文件用于说明**项目交付证据管理系统**在 MVP 阶段的业务场景设计、权限模型设计以及数据库表结构设计，明确：

- 系统解决的核心问题
- 业务场景如何映射到数据库设计
- 各类数据对象的职责边界

为系统开发、联调、验收及后续扩展提供统一依据。

### 1.2 适用范围

- 项目交付证据管理系统（MVP 阶段）
- 后端数据库设计与实现
- 项目交付与审计说明

### 1.3 设计原则

- **最小可用原则（MVP）**：仅覆盖核心业务场景
- **证据不可随意消失原则**：不进行物理删除，保留证据链
- **权限清晰可解释原则**：采用项目级 ACL 权限模型
- **可审计原则**：所有关键操作均留痕

---

## 2. 系统业务场景概述

### 2.1 核心业务目标

系统用于集中管理项目实施过程中的关键交付证据（照片、文档、说明等），实现：

- 按项目归档证据
- 按用户权限访问证据
- 证据全过程留痕、可追溯

### 2.2 MVP 业务角色定义

| 角色 | 含义 | 说明 |
|---|---|---|
| owner | 项目负责人 | 对项目与证据负最终责任 |
| editor | 项目成员 | 负责上传、维护证据 |
| viewer | 查看人员 | 仅查看、下载证据 |

---

## 3. 核心业务场景说明

### 3.1 管理员预置用户

- 系统不提供自助注册功能
- 用户由管理员统一创建
- 用户作为系统所有操作的主体

涉及数据表：
- `auth_user`

---

### 3.2 创建项目

- 登录用户创建项目
- 系统自动赋予创建者该项目的 `owner` 权限
- 项目作为证据的归属单元

涉及数据表：
- `project`
- `auth_project_acl`
- `audit_operation_log`

---

### 3.3 项目授权（ACL）

- 仅 `owner` 可对项目成员授权
- 授权粒度为项目级
- 不引入复杂 RBAC 模型

涉及数据表：
- `auth_project_acl`
- `audit_operation_log`

---

### 3.4 上传证据（核心场景）

- `editor / owner` 上传文件到对象存储（MinIO）
- 系统保存证据元数据与对象指针
- 上传行为写入审计日志

涉及数据表：
- `evidence_item`
- `audit_operation_log`

---

### 3.5 浏览证据列表（默认行为）

- 用户仅可查看自己有权限的项目证据
- 默认只展示 `status = active` 的证据
- 标记为误传的证据默认不显示

涉及数据表：
- `auth_project_acl`
- `evidence_item`
- `auth_user`

---

### 3.6 下载证据

- `viewer / editor / owner` 均可下载证据
- 每一次下载操作均记录审计日志

涉及数据表：
- `evidence_item`
- `audit_operation_log`

---

### 3.7 标记误传

- `editor` 只能标记**自己上传**的证据为误传
- `owner` 可标记任何证据
- 标记误传后证据状态变为 `invalid`
- 默认列表不再显示该证据

涉及数据表：
- `evidence_item`
- `audit_operation_log`

---

### 3.8 误传证据处置（owner）

- `owner` 可查看误传证据列表
- 可对误传证据进行：
  - 恢复为有效
  - 归档处理
- 所有操作写入审计日志

涉及数据表：
- `evidence_item`
- `audit_operation_log`

---

## 4. 权限模型与业务规则总结

### 4.1 权限模型

- 采用**项目级 ACL 权限模型**
- 一个用户在一个项目中仅拥有一个角色

### 4.2 关键业务规则

- editor 无权直接删除证据
- editor 可标记误传（软操作）
- owner 负责最终裁决
- 系统不进行物理删除，保证证据链完整

---

## 5. 数据库设计总览

### 5.1 数据表清单

| 序号 | 表名 | 说明 |
|---|---|---|
| 1 | auth_user | 系统用户表 |
| 2 | project | 项目表 |
| 3 | auth_project_acl | 项目权限表 |
| 4 | evidence_item | 证据元数据表 |
| 5 | audit_operation_log | 操作审计日志表 |

---

## 6. 数据表结构设计说明

### 6.1 用户表 auth_user

| 中文字段名 | 英文字段名 | 类型 | 说明 |
|---|---|---|---|
| 用户ID | id | UUID | 主键 |
| 登录名 | username | TEXT | 唯一登录账号 |
| 显示名称 | display_name | TEXT | 用户姓名或昵称 |
| 邮箱 | email | TEXT | 联系邮箱 |
| 是否启用 | is_active | BOOLEAN | 用户状态 |
| 创建时间 | created_at | TIMESTAMPTZ | 创建时间 |

---

### 6.2 项目表 project

| 中文字段名 | 英文字段名 | 类型 | 说明 |
|---|---|---|---|
| 项目ID | id | BIGSERIAL | 主键 |
| 项目编号 | code | TEXT | 项目编号 |
| 项目名称 | name | TEXT | 项目名称 |
| 项目描述 | description | TEXT | 项目说明 |
| 项目状态 | status | TEXT | active / archived |
| 创建人 | created_by | UUID | 创建用户 |
| 创建时间 | created_at | TIMESTAMPTZ | 创建时间 |
| 更新时间 | updated_at | TIMESTAMPTZ | 更新时间 |

---

### 6.3 项目权限表 auth_project_acl

| 中文字段名 | 英文字段名 | 类型 | 说明 |
|---|---|---|---|
| 记录ID | id | BIGSERIAL | 主键 |
| 项目ID | project_id | BIGINT | 所属项目 |
| 用户ID | user_id | UUID | 授权用户 |
| 项目角色 | role | TEXT | owner / editor / viewer |
| 授权时间 | created_at | TIMESTAMPTZ | 授权时间 |

---

### 6.4 证据元数据表 evidence_item

| 中文字段名 | 英文字段名 | 类型 | 说明 |
|---|---|---|---|
| 证据ID | id | BIGSERIAL | 主键 |
| 项目ID | project_id | BIGINT | 所属项目 |
| 证据标题 | title | TEXT | 证据标题 |
| 证据说明 | note | TEXT | 补充说明 |
| 存储桶 | bucket | TEXT | MinIO Bucket |
| 对象路径 | object_key | TEXT | MinIO Key |
| 文件类型 | content_type | TEXT | MIME 类型 |
| 文件大小 | size_bytes | BIGINT | 字节大小 |
| ETag | etag | TEXT | 校验标识 |
| 状态 | status | TEXT | active / invalid / archived |
| 创建人 | created_by | UUID | 上传人 |
| 创建时间 | created_at | TIMESTAMPTZ | 上传时间 |
| 更新时间 | updated_at | TIMESTAMPTZ | 更新时间 |
| 误传原因 | invalid_reason | TEXT | 误传说明 |
| 误传人 | invalid_by | UUID | 操作人 |
| 误传时间 | invalid_at | TIMESTAMPTZ | 操作时间 |

---

### 6.5 审计日志表 audit_operation_log

| 中文字段名 | 英文字段名 | 类型 | 说明 |
|---|---|---|---|
| 日志ID | id | BIGSERIAL | 主键 |
| 操作人 | actor_user_id | UUID | 操作用户 |
| 操作类型 | action | TEXT | upload / mark_invalid 等 |
| 目标类型 | target_type | TEXT | project / evidence_item |
| 目标ID | target_id | TEXT | 操作对象ID |
| 操作详情 | detail | JSONB | 扩展信息 |
| 操作时间 | created_at | TIMESTAMPTZ | 操作时间 |

---

## 7. 总结

本数据库设计与业务场景说明在 MVP 阶段已满足以下要求：

- 业务场景与数据结构一一对应
- 权限规则清晰、可解释
- 证据全流程可追溯、可审计
- 后续可平滑扩展至正式版本

该设计可作为系统开发、测试、验收及交付的统一依据。

