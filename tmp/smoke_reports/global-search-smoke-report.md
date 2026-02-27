# 全局证据搜索接口冒烟测试报告

- 执行时间：2026-02-27 16:05:20
- API_BASE：http://localhost:8081

## 鉴权与登录方式

- 登录接口：`POST /api/auth/login`，请求体：`{ "username": "...", "password": "..." }`
- 鉴权方式：**基于 Session 的 Cookie**（后端 `AuthService` 在 Session 中写入 `LOGIN_USER_ID`，`AuthInterceptor` 从 Session 读取并注入当前用户）
- 测试脚本会优先使用 `smoke_global_search.env` 中的 `TEST_USER_*` / `TEST_PASS_*` 自动登录获取 Session Cookie；
  - 若登录失败或未配置账号，则回退尝试使用 `AUTH_COOKIE_*` / `AUTH_TOKEN_*` 环境变量；
  - 未登录访问 `GET /api/evidence/global-search` 预期返回 HTTP 401。

## 用例结果

| 用例编号 | 结果 | 说明 |
|----------|------|------|
| TC-01 未登录 401 | PASS | 未带凭证访问返回 401（实际：401） |
| TC-02 登录后成功结构 | FAIL | 响应 code 期望为 0，实际：500 |

### TC-02 登录后成功结构 失败调试信息（已脱敏）

```bash
# 使用与脚本一致的 API_BASE：http://localhost:8081
curl -X GET "http://localhost:8081/api/evidence/global-search?keyword=a&page=1&pageSize=10" \
  # 如需带登录态，请在本地补充：-b <cookie_jar> 或 -H 'Cookie: <REDACTED>'
```

| TC-03 keyword 缺失 -> 空分页 | FAIL | 期望 code=0,total=0,records=[]，实际：code=500,total=?,len=0 |

### TC-03 keyword 缺失 -> 空分页 失败调试信息（已脱敏）

```bash
# 使用与脚本一致的 API_BASE：http://localhost:8081
curl -X GET "http://localhost:8081/api/evidence/global-search?page=1&pageSize=10" \
  # 如需带登录态，请在本地补充：-b <cookie_jar> 或 -H 'Cookie: <REDACTED>'
```

| TC-04 keyword 为空/空白 -> 空分页 | FAIL | keyword 为空/空白时未返回空分页（code/total/len 不符合预期） |

### TC-04 keyword 为空/空白 -> 空分页 失败调试信息（已脱敏）

```bash
# 使用与脚本一致的 API_BASE：http://localhost:8081
curl -X GET "http://localhost:8081/api/evidence/global-search?keyword=&page=1&pageSize=10" \
  # 如需带登录态，请在本地补充：-b <cookie_jar> 或 -H 'Cookie: <REDACTED>'
```


#### TC-05 title 模糊命中（ILIKE） 详情

- 使用关键字：`项目`
- 返回 total：0
- 前若干条标题示例：

| TC-05 title 模糊命中（ILIKE） | WARN | 未命中任何标题，可能是测试数据不足或关键字与现有数据不匹配 |

#### TC-06 real_name 命中 详情

- 使用关键字：`系统管理员`
- 返回 total：0
- 前若干条上传人（createdByDisplayName）示例：

| TC-06 real_name 命中 | WARN | 未命中任何 real_name，可能是测试数据不足或关键字与现有数据不匹配 |

#### TC-07 username 命中 详情

- 使用关键字：`admin`
- 返回 total：0
- 前若干条上传人（createdByDisplayName）示例：

| TC-07 username 命中 | WARN | 未命中任何 username，可能是测试数据不足或关键字与现有数据不匹配 |
| TC-08 大小写不敏感 | WARN | 大小写两次搜索均无命中，可能是测试数据不足或关键字错误 |
| TC-09 权限可见项目过滤（A/B 对比） | WARN | 未配置用户 B 凭证，本用例跳过（建议提供不同角色帐号以人工对比可见项目） |
| TC-10 INVALID 默认排除 | PASS | 未发现 evidenceStatus=INVALID 的记录 |
| TC-11 排序 created_at DESC | WARN | 无 createdAt 数据或 records 为空，无法验证排序 |
| TC-12 分页不重复 | WARN | 数据不足以验证分页去重（某一页 records 为空） |
| TC-13 pageSize 边界 | WARN | 响应中未包含 data.pageSize 字段，无法验证 pageSize 边界 |
| TC-14 latestVersion 填充（弱断言） | WARN | records 为空，无法验证 latestVersion |
| TC-15 特殊字符不 500 | PASS | 特殊字符 %25 与 _ 请求未返回 500（实际：200/200） |

## 汇总

- PASS 用例数：3
- FAIL 用例数：3
- WARN 用例数：9

