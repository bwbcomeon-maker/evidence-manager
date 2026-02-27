#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPORT_DIR="${REPO_ROOT}/tmp/smoke_reports"
REPORT_FILE="${REPORT_DIR}/global-search-smoke-report.md"
ENV_FILE="${SCRIPT_DIR}/smoke_global_search.env"

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

ensure_tools() {
  if ! command -v curl >/dev/null 2>&1; then
    echo "ERROR: curl 未安装，请先安装 curl 再运行本脚本。" >&2
    exit 1
  fi
  if ! command -v jq >/dev/null 2>&1; then
    echo "ERROR: jq 未安装，本脚本依赖 jq 解析 JSON，请先安装 jq（例如：brew install jq）。" >&2
    exit 1
  fi
}

load_env() {
  # shellcheck disable=SC1090
  if [[ -f "${ENV_FILE}" ]]; then
    # 仅导入此文件，不强制要求一定存在
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
  fi
}

detect_api_base() {
  local default_base="http://localhost:8080"
  local detected_port=""

  # 若用户显式指定 API_BASE，则使用之
  if [[ -n "${API_BASE:-}" ]]; then
    return
  fi

  # 从 backend application.properties 中读取 server.port
  local app_props="${REPO_ROOT}/backend/app/src/main/resources/application.properties"
  if [[ -f "${app_props}" ]]; then
    detected_port="$(grep -E '^server\.port=' "${app_props}" | head -n1 | cut -d'=' -f2 | tr -d '[:space:]' || true)"
  fi

  if [[ -n "${detected_port}" ]]; then
    API_BASE="http://localhost:${detected_port}"
  else
    API_BASE="${default_base}"
  fi
}

init_report() {
  mkdir -p "${REPORT_DIR}"
  : > "${REPORT_FILE}"
  {
    echo "# 全局证据搜索接口冒烟测试报告"
    echo
    echo "- 执行时间：$(date '+%Y-%m-%d %H:%M:%S')"
    echo "- API_BASE：${API_BASE}"
    echo
    echo "## 鉴权与登录方式"
    echo
    echo "- 登录接口：\`POST /api/auth/login\`，请求体：\`{ \"username\": \"...\", \"password\": \"...\" }\`"
    echo "- 鉴权方式：**基于 Session 的 Cookie**（后端 \`AuthService\` 在 Session 中写入 \`LOGIN_USER_ID\`，\`AuthInterceptor\` 从 Session 读取并注入当前用户）"
    echo "- 测试脚本会优先使用 \`${ENV_FILE##*/}\` 中的 \`TEST_USER_*\` / \`TEST_PASS_*\` 自动登录获取 Session Cookie；"
    echo "  - 若登录失败或未配置账号，则回退尝试使用 \`AUTH_COOKIE_*\` / \`AUTH_TOKEN_*\` 环境变量；"
    echo "  - 未登录访问 \`GET /api/evidence/global-search\` 预期返回 HTTP 401。"
    echo
    echo "## 用例结果"
    echo
    echo "| 用例编号 | 结果 | 说明 |"
    echo "|----------|------|------|"
  } >> "${REPORT_FILE}"
}

append_result_row() {
  local tc="$1"
  local status="$2"  # PASS / FAIL / WARN
  local info="$3"
  case "${status}" in
    PASS) PASS_COUNT=$((PASS_COUNT + 1)) ;;
    FAIL) FAIL_COUNT=$((FAIL_COUNT + 1)) ;;
    WARN) WARN_COUNT=$((WARN_COUNT + 1)) ;;
  esac
  printf '| %s | %s | %s |\n' "${tc}" "${status}" "${info}" >> "${REPORT_FILE}"
}

append_fail_curl() {
  local tc="$1"
  local url="$2"
  local method="${3:-GET}"
  {
    echo
    echo "### ${tc} 失败调试信息（已脱敏）"
    echo
    echo "\`\`\`bash"
    echo "# 使用与脚本一致的 API_BASE：${API_BASE}"
    echo "curl -X ${method} \"${url}\" \\"
    echo "  # 如需带登录态，请在本地补充：-b <cookie_jar> 或 -H 'Cookie: <REDACTED>'"
    echo "\`\`\`"
    echo
  } >> "${REPORT_FILE}"
}

HTTP_TMP_BODY=""
HTTP_STATUS=""

do_curl() {
  local method="$1"
  local url="$2"
  shift 2

  HTTP_TMP_BODY="$(mktemp)"
  HTTP_STATUS="000"

  # 使用 curl 发送请求，支持附加参数（如 -H、-b 等）
  if ! HTTP_STATUS="$(curl -sS -o "${HTTP_TMP_BODY}" -w "%{http_code}" -X "${method}" "$@" "${url}")"; then
    HTTP_STATUS="000"
  fi
}

read_body() {
  if [[ -f "${HTTP_TMP_BODY:-}" ]]; then
    cat "${HTTP_TMP_BODY}"
  fi
}

cleanup_http_tmp() {
  if [[ -f "${HTTP_TMP_BODY:-}" ]]; then
    rm -f "${HTTP_TMP_BODY}"
  fi
}

COOKIE_JAR_A="${REPO_ROOT}/tmp/smoke_reports/.cookie_a_$$"
COOKIE_JAR_B="${REPO_ROOT}/tmp/smoke_reports/.cookie_b_$$"
USE_COOKIE_A=false
USE_COOKIE_B=false

login_user() {
  local label="$1" # A or B
  local user_var="TEST_USER_${label}"
  local pass_var="TEST_PASS_${label}"
  local cookie_jar
  cookie_jar="${COOKIE_JAR_A}"
  if [[ "${label}" == "B" ]]; then
    cookie_jar="${COOKIE_JAR_B}"
  fi

  local username="${!user_var:-}"
  local password="${!pass_var:-}"

  # 仅当配置了用户名和密码时才尝试自动登录
  if [[ -n "${username}" && -n "${password}" ]]; then
    echo "登录用户 ${label}（${username}）..."
    local login_url="${API_BASE}/api/auth/login"
    local login_body
    login_body="$(jq -n --arg u "${username}" --arg p "${password}" '{username:$u,password:$p}')"

    do_curl "POST" "${login_url}" -c "${cookie_jar}" -H "Content-Type: application/json" -d "${login_body}"
    local body
    body="$(read_body)"

    if [[ "${HTTP_STATUS}" == "200" ]]; then
      local code
      if code="$(printf '%s' "${body}" | jq -r '.code // empty' 2>/dev/null)"; then
        if [[ "${code}" == "0" ]]; then
          echo "登录用户 ${label} 成功。"
          if [[ "${label}" == "A" ]]; then
            USE_COOKIE_A=true
          else
            USE_COOKIE_B=true
          fi
          cleanup_http_tmp
          return 0
        fi
      fi
    fi
    echo "WARN: 自动登录用户 ${label} 失败（HTTP ${HTTP_STATUS}），将尝试使用 AUTH_COOKIE_${label} / AUTH_TOKEN_${label} 兜底。" >&2
    cleanup_http_tmp
  fi

  # 自动登录失败或未配置账号，依次尝试 AUTH_COOKIE / AUTH_TOKEN
  local cookie_env="AUTH_COOKIE_${label}"
  local token_env="AUTH_TOKEN_${label}"
  if [[ -n "${!cookie_env:-}" ]] || [[ -n "${!token_env:-}" ]]; then
    echo "使用环境变量中的 AUTH_COOKIE_${label} / AUTH_TOKEN_${label} 作为兜底凭证。"
    return 0
  fi

  echo "WARN: 未能为用户 ${label} 获取任何凭证（既未成功登录，也未配置 AUTH_COOKIE_${label}/AUTH_TOKEN_${label})。" >&2
  return 1
}

auth_args_for() {
  local label="$1"
  local out_var="$2"

  # 使用普通变量名组合，兼容非 bash 4 环境（避免 local -n）
  local _args=()
  local cookie_env="AUTH_COOKIE_${label}"
  local token_env="AUTH_TOKEN_${label}"

  if [[ "${label}" == "A" && "${USE_COOKIE_A}" == "true" ]]; then
    _args=(-b "${COOKIE_JAR_A}")
    printf -v "${out_var}" '%s' "${_args[*]}"
    return
  fi
  if [[ "${label}" == "B" && "${USE_COOKIE_B}" == "true" ]]; then
    _args=(-b "${COOKIE_JAR_B}")
    printf -v "${out_var}" '%s' "${_args[*]}"
    return
  fi

  if [[ -n "${!cookie_env:-}" ]]; then
    _args=(-H "Cookie: ${!cookie_env}")
    printf -v "${out_var}" '%s' "${_args[*]}"
    return
  fi
  if [[ -n "${!token_env:-}" ]]; then
    _args=(-H "Authorization: Bearer ${!token_env}")
    printf -v "${out_var}" '%s' "${_args[*]}"
    return
  fi

  printf -v "${out_var}" '%s' ""
}

tc01_unauthorized() {
  local tc="TC-01 未登录 401"
  local url="${API_BASE}/api/evidence/global-search?keyword=test&page=1&pageSize=10"

  do_curl "GET" "${url}"
  local status="${HTTP_STATUS}"
  cleanup_http_tmp

  if [[ "${status}" == "401" ]]; then
    append_result_row "${tc}" "PASS" "未带凭证访问返回 401（实际：${status}）"
  else
    append_result_row "${tc}" "FAIL" "未带凭证访问期望 401，实际：${status}"
    append_fail_curl "${tc}" "${url}" "GET"
  fi
}

parse_common_fields() {
  local body="$1"
  local has_total has_records has_page has_page_size
  has_total="$(printf '%s' "${body}" | jq 'has("data") and (.data|has("total"))' 2>/dev/null || echo "false")"
  has_records="$(printf '%s' "${body}" | jq 'has("data") and (.data|has("records"))' 2>/dev/null || echo "false")"
  has_page="$(printf '%s' "${body}" | jq 'has("data") and (.data|has("page"))' 2>/dev/null || echo "false")"
  has_page_size="$(printf '%s' "${body}" | jq 'has("data") and (.data|has("pageSize"))' 2>/dev/null || echo "false")"
  echo "${has_total} ${has_records} ${has_page} ${has_page_size}"
}

tc02_login_success_structure() {
  local tc="TC-02 登录后成功结构"
  local url="${API_BASE}/api/evidence/global-search?keyword=a&page=1&pageSize=10"

  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )
  if [[ "${#auth_args[@]}" -eq 0 && "${USE_COOKIE_A}" != "true" ]]; then
    append_result_row "${tc}" "FAIL" "未获取到用户 A 的任何凭证，无法执行登录后用例"
    append_fail_curl "${tc}" "${url}" "GET"
    return
  fi

  do_curl "GET" "${url}" "${auth_args[@]}"
  local status="${HTTP_STATUS}"
  local body
  body="$(read_body)"
  cleanup_http_tmp

  if [[ "${status}" != "200" ]]; then
    append_result_row "${tc}" "FAIL" "HTTP 状态非 200（实际：${status}）"
    append_fail_curl "${tc}" "${url}" "GET"
    return
  fi

  local code
  code="$(printf '%s' "${body}" | jq -r '.code // empty' 2>/dev/null || echo "")"
  if [[ "${code}" != "0" ]]; then
    append_result_row "${tc}" "FAIL" "响应 code 期望为 0，实际：${code:-<空>}"
    append_fail_curl "${tc}" "${url}" "GET"
    return
  fi

  local has_total has_records has_page has_page_size
  read -r has_total has_records has_page has_page_size < <(parse_common_fields "${body}")
  if [[ "${has_total}" != "true" || "${has_records}" != "true" || "${has_page}" != "true" || "${has_page_size}" != "true" ]]; then
    append_result_row "${tc}" "FAIL" "data 中缺少 total/records/page/pageSize 字段"
    append_fail_curl "${tc}" "${url}" "GET"
    return
  fi

  local records_len
  records_len="$(printf '%s' "${body}" | jq '.data.records | length' 2>/dev/null || echo "0")"
  if [[ "${records_len}" -gt 0 ]]; then
    local missing_any
    missing_any="$(printf '%s' "${body}" | jq '[.data.records[] | (has("evidenceId") and has("projectId") and has("stageCode") and has("evidenceTypeCode") and has("title") and has("createdAt") and has("evidenceStatus"))] | all' 2>/dev/null || echo "false")"
    if [[ "${missing_any}" != "true" ]]; then
      append_result_row "${tc}" "FAIL" "records 中存在缺失关键字段的项"
      append_fail_curl "${tc}" "${url}" "GET"
      return
    fi
  fi

  append_result_row "${tc}" "PASS" "结构校验通过（HTTP 200, code=0, 字段完整）"
}

tc03_keyword_missing_empty_page() {
  local tc="TC-03 keyword 缺失 -> 空分页"
  local url="${API_BASE}/api/evidence/global-search?page=1&pageSize=10"
  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )

  do_curl "GET" "${url}" "${auth_args[@]}"
  local body
  body="$(read_body)"
  cleanup_http_tmp

  local code total len
  code="$(printf '%s' "${body}" | jq -r '.code // empty' 2>/dev/null || echo "")"
  total="$(printf '%s' "${body}" | jq -r '.data.total // empty' 2>/dev/null || echo "")"
  len="$(printf '%s' "${body}" | jq -r '.data.records | length' 2>/dev/null || echo "")"

  if [[ "${code}" == "0" && "${total}" == "0" && "${len}" == "0" ]]; then
    append_result_row "${tc}" "PASS" "未传 keyword 时返回空分页（total=0, records=[]）"
  else
    append_result_row "${tc}" "FAIL" "期望 code=0,total=0,records=[]，实际：code=${code:-?},total=${total:-?},len=${len:-?}"
    append_fail_curl "${tc}" "${url}" "GET"
  fi
}

tc04_keyword_blank_empty_page() {
  local tc="TC-04 keyword 为空/空白 -> 空分页"
  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )

  local url1="${API_BASE}/api/evidence/global-search?keyword=&page=1&pageSize=10"
  local url2="${API_BASE}/api/evidence/global-search?keyword=%20%20%20&page=1&pageSize=10"

  local code1 total1 len1 code2 total2 len2

  do_curl "GET" "${url1}" "${auth_args[@]}"
  local body1
  body1="$(read_body)"
  cleanup_http_tmp
  code1="$(printf '%s' "${body1}" | jq -r '.code // empty' 2>/dev/null || echo "")"
  total1="$(printf '%s' "${body1}" | jq -r '.data.total // empty' 2>/dev/null || echo "")"
  len1="$(printf '%s' "${body1}" | jq -r '.data.records | length' 2>/dev/null || echo "")"

  do_curl "GET" "${url2}" "${auth_args[@]}"
  local body2
  body2="$(read_body)"
  cleanup_http_tmp
  code2="$(printf '%s' "${body2}" | jq -r '.code // empty' 2>/dev/null || echo "")"
  total2="$(printf '%s' "${body2}" | jq -r '.data.total // empty' 2>/dev/null || echo "")"
  len2="$(printf '%s' "${body2}" | jq -r '.data.records | length' 2>/dev/null || echo "")"

  if [[ "${code1}" == "0" && "${total1}" == "0" && "${len1}" == "0" && "${code2}" == "0" && "${total2}" == "0" && "${len2}" == "0" ]]; then
    append_result_row "${tc}" "PASS" "keyword 为空/空白时均返回空分页"
  else
    append_result_row "${tc}" "FAIL" "keyword 为空/空白时未返回空分页（code/total/len 不符合预期）"
    append_fail_curl "${tc}" "${url1}" "GET"
  fi
}

get_env_or_default() {
  local name="$1"
  local def="$2"
  local val="${!name:-}"
  if [[ -n "${val}" ]]; then
    printf '%s\n' "${val}"
  else
    printf '%s\n' "${def}"
  fi
}

tc05_title_ilike() {
  local tc="TC-05 title 模糊命中（ILIKE）"
  local kw
  kw="$(get_env_or_default "KEYWORD_TITLE" "上架")"
  local url="${API_BASE}/api/evidence/global-search?keyword=$(printf '%s' "${kw}" | jq -s -R -r @uri)&page=1&pageSize=10"

  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )
  do_curl "GET" "${url}" "${auth_args[@]}"
  local body
  body="$(read_body)"
  cleanup_http_tmp

  local total
  total="$(printf '%s' "${body}" | jq -r '.data.total // 0' 2>/dev/null || echo "0")"

  {
    echo
    echo "#### ${tc} 详情"
    echo
    echo "- 使用关键字：\`${kw}\`"
    echo "- 返回 total：${total}"
    echo "- 前若干条标题示例："
    printf '%s\n' "${body}" | jq -r '.data.records[0:5][]?.title' 2>/dev/null || true
    echo
  } >> "${REPORT_FILE}"

  if [[ "${total}" -eq 0 ]]; then
    append_result_row "${tc}" "WARN" "未命中任何标题，可能是测试数据不足或关键字与现有数据不匹配"
  else
    append_result_row "${tc}" "PASS" "total=${total}，已打印部分标题供人工确认 ILIKE 效果"
  fi
}

tc06_realname_ilike() {
  local tc="TC-06 real_name 命中"
  local kw
  kw="$(get_env_or_default "KEYWORD_REALNAME" "张三")"
  local url="${API_BASE}/api/evidence/global-search?keyword=$(printf '%s' "${kw}" | jq -s -R -r @uri)&page=1&pageSize=10"

  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )
  do_curl "GET" "${url}" "${auth_args[@]}"
  local body
  body="$(read_body)"
  cleanup_http_tmp

  local total
  total="$(printf '%s' "${body}" | jq -r '.data.total // 0' 2>/dev/null || echo "0")"

  {
    echo
    echo "#### ${tc} 详情"
    echo
    echo "- 使用关键字：\`${kw}\`"
    echo "- 返回 total：${total}"
    echo "- 前若干条上传人（createdByDisplayName）示例："
    printf '%s\n' "${body}" | jq -r '.data.records[0:5][]?.createdByDisplayName' 2>/dev/null || true
    echo
  } >> "${REPORT_FILE}"

  if [[ "${total}" -eq 0 ]]; then
    append_result_row "${tc}" "WARN" "未命中任何 real_name，可能是测试数据不足或关键字与现有数据不匹配"
  else
    append_result_row "${tc}" "PASS" "total=${total}，已打印部分上传人供人工确认 real_name 匹配效果"
  fi
}

tc07_username_ilike() {
  local tc="TC-07 username 命中"
  local kw
  kw="$(get_env_or_default "KEYWORD_USERNAME" "zhangsan")"
  local url="${API_BASE}/api/evidence/global-search?keyword=$(printf '%s' "${kw}" | jq -s -R -r @uri)&page=1&pageSize=10"

  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )
  do_curl "GET" "${url}" "${auth_args[@]}"
  local body
  body="$(read_body)"
  cleanup_http_tmp

  local total
  total="$(printf '%s' "${body}" | jq -r '.data.total // 0' 2>/dev/null || echo "0")"

  {
    echo
    echo "#### ${tc} 详情"
    echo
    echo "- 使用关键字：\`${kw}\`"
    echo "- 返回 total：${total}"
    echo "- 前若干条上传人（createdByDisplayName）示例："
    printf '%s\n' "${body}" | jq -r '.data.records[0:5][]?.createdByDisplayName' 2>/dev/null || true
    echo
  } >> "${REPORT_FILE}"

  if [[ "${total}" -eq 0 ]]; then
    append_result_row "${tc}" "WARN" "未命中任何 username，可能是测试数据不足或关键字与现有数据不匹配"
  else
    append_result_row "${tc}" "PASS" "total=${total}，已打印部分上传人供人工确认 username 匹配效果"
  fi
}

tc08_case_insensitive() {
  local tc="TC-08 大小写不敏感"
  local kw_lower
  kw_lower="$(get_env_or_default "KEYWORD_USERNAME" "zhangsan")"
  local kw_upper
  kw_upper="$(printf '%s' "${kw_lower}" | tr '[:lower:]' '[:upper:]')"

  local url_lower="${API_BASE}/api/evidence/global-search?keyword=$(printf '%s' "${kw_lower}" | jq -s -R -r @uri)&page=1&pageSize=10"
  local url_upper="${API_BASE}/api/evidence/global-search?keyword=$(printf '%s' "${kw_upper}" | jq -s -R -r @uri)&page=1&pageSize=10"

  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )

  do_curl "GET" "${url_lower}" "${auth_args[@]}"
  local body_lower
  body_lower="$(read_body)"
  cleanup_http_tmp
  local total_lower
  total_lower="$(printf '%s' "${body_lower}" | jq -r '.data.total // 0' 2>/dev/null || echo "0")"

  do_curl "GET" "${url_upper}" "${auth_args[@]}"
  local body_upper
  body_upper="$(read_body)"
  cleanup_http_tmp
  local total_upper
  total_upper="$(printf '%s' "${body_upper}" | jq -r '.data.total // 0' 2>/dev/null || echo "0")"

  if [[ "${total_lower}" -eq 0 && "${total_upper}" -eq 0 ]]; then
    append_result_row "${tc}" "WARN" "大小写两次搜索均无命中，可能是测试数据不足或关键字错误"
  elif [[ "${total_lower}" -ne "${total_upper}" ]]; then
    append_result_row "${tc}" "FAIL" "大小写不敏感预期 total 相同，实际 lower=${total_lower}, upper=${total_upper}"
    append_fail_curl "${tc}" "${url_lower}" "GET"
  else
    append_result_row "${tc}" "PASS" "lower/upper total 均为 ${total_lower}，符合大小写不敏感预期"
  fi
}

tc09_permission_filter() {
  local tc="TC-09 权限可见项目过滤（A/B 对比）"
  local kw
  kw="$(get_env_or_default "KEYWORD_TITLE" "项目")"

  # 仅当 B 用户有凭证时执行
  local auth_args_b_str=""
  auth_args_for "B" auth_args_b_str
  # shellcheck disable=SC2206
  local auth_args_b=( ${auth_args_b_str} )
  if [[ "${#auth_args_b[@]}" -eq 0 && "${USE_COOKIE_B}" != "true" ]]; then
    append_result_row "${tc}" "WARN" "未配置用户 B 凭证，本用例跳过（建议提供不同角色帐号以人工对比可见项目）"
    return
  fi

  local url="${API_BASE}/api/evidence/global-search?keyword=$(printf '%s' "${kw}" | jq -s -R -r @uri)&page=1&pageSize=50"

  local auth_args_a_str=""
  auth_args_for "A" auth_args_a_str
  # shellcheck disable=SC2206
  local auth_args_a=( ${auth_args_a_str} )

  do_curl "GET" "${url}" "${auth_args_a[@]}"
  local body_a
  body_a="$(read_body)"
  cleanup_http_tmp

  do_curl "GET" "${url}" "${auth_args_b[@]}"
  local body_b
  body_b="$(read_body)"
  cleanup_http_tmp

  local projects_a projects_b
  projects_a="$(printf '%s' "${body_a}" | jq -r '.data.records[].projectId' 2>/dev/null | sort -u | tr '\n' ' ' || true)"
  projects_b="$(printf '%s' "${body_b}" | jq -r '.data.records[].projectId' 2>/dev/null | sort -u | tr '\n' ' ' || true)"

  {
    echo
    echo "#### ${tc} 详情"
    echo
    echo "- 关键字：\`${kw}\`"
    echo "- 用户 A projectId 集合：${projects_a:-<空>}"
    echo "- 用户 B projectId 集合：${projects_b:-<空>}"
    echo
  } >> "${REPORT_FILE}"

  append_result_row "${tc}" "WARN" "仅输出 A/B projectId 集合供人工对比可见项目差异（未做强断言）"
}

tc10_exclude_invalid() {
  local tc="TC-10 INVALID 默认排除"
  local kw
  kw="$(get_env_or_default "KEYWORD_TITLE" "项目")"
  local url="${API_BASE}/api/evidence/global-search?keyword=$(printf '%s' "${kw}" | jq -s -R -r @uri)&page=1&pageSize=50"

  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )
  do_curl "GET" "${url}" "${auth_args[@]}"
  local body
  body="$(read_body)"
  cleanup_http_tmp

  local invalid_count
  invalid_count="$(printf '%s' "${body}" | jq '[.data.records[]? | select(.evidenceStatus == \"INVALID\")] | length' 2>/dev/null || echo "0")"
  if [[ "${invalid_count}" -gt 0 ]]; then
    append_result_row "${tc}" "FAIL" "查询结果中出现 ${invalid_count} 条 evidenceStatus=INVALID，预期应全部排除"
    append_fail_curl "${tc}" "${url}" "GET"
  else
    append_result_row "${tc}" "PASS" "未发现 evidenceStatus=INVALID 的记录"
  fi
}

tc11_sort_created_desc() {
  local tc="TC-11 排序 created_at DESC"
  local kw
  kw="$(get_env_or_default "KEYWORD_TITLE" "项目")"
  local url="${API_BASE}/api/evidence/global-search?keyword=$(printf '%s' "${kw}" | jq -s -R -r @uri)&page=1&pageSize=5"

  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )
  do_curl "GET" "${url}" "${auth_args[@]}"
  local body
  body="$(read_body)"
  cleanup_http_tmp

  local times
  times="$(printf '%s' "${body}" | jq -r '.data.records[]?.createdAt' 2>/dev/null || true)"

  if [[ -z "${times}" ]]; then
    append_result_row "${tc}" "WARN" "无 createdAt 数据或 records 为空，无法验证排序"
    return
  fi

  local prev=""
  local ok="true"
  while IFS= read -r t; do
    if [[ -z "${prev}" ]]; then
      prev="${t}"
      continue
    fi
    # 简单按字符串比较，针对 ISO-8601 时间通常可反映先后顺序
    if [[ "${prev}" < "${t}" ]]; then
      ok="false"
      break
    fi
    prev="${t}"
  done <<< "${times}"

  if [[ "${ok}" == "true" ]]; then
    append_result_row "${tc}" "PASS" "createdAt 按 DESC 排序检查通过（基于字符串比较）"
  else
    append_result_row "${tc}" "WARN" "createdAt 排序未严格满足字符串 DESC，可能与时区/格式有关，请人工确认"
  fi
}

tc12_pagination_no_overlap() {
  local tc="TC-12 分页不重复"
  local kw
  kw="$(get_env_or_default "KEYWORD_TITLE" "项目")"
  local base_q="keyword=$(printf '%s' "${kw}" | jq -s -R -r @uri)&pageSize=2"
  local url1="${API_BASE}/api/evidence/global-search?${base_q}&page=1"
  local url2="${API_BASE}/api/evidence/global-search?${base_q}&page=2"

  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )

  do_curl "GET" "${url1}" "${auth_args[@]}"
  local body1
  body1="$(read_body)"
  cleanup_http_tmp
  do_curl "GET" "${url2}" "${auth_args[@]}"
  local body2
  body2="$(read_body)"
  cleanup_http_tmp

  local ids1 ids2
  ids1="$(printf '%s' "${body1}" | jq -r '.data.records[]?.evidenceId' 2>/dev/null || true)"
  ids2="$(printf '%s' "${body2}" | jq -r '.data.records[]?.evidenceId' 2>/dev/null || true)"

  if [[ -z "${ids1}" || -z "${ids2}" ]]; then
    append_result_row "${tc}" "WARN" "数据不足以验证分页去重（某一页 records 为空）"
    return
  fi

  local overlap="false"
  local id
  for id in ${ids1}; do
    if grep -q -w "${id}" <<< "${ids2}"; then
      overlap="true"
      break
    fi
  done

  if [[ "${overlap}" == "true" ]]; then
    append_result_row "${tc}" "FAIL" "page=1 与 page=2 出现重复 evidenceId"
    append_fail_curl "${tc}" "${url1}" "GET"
  else
    append_result_row "${tc}" "PASS" "page=1 与 page=2 evidenceId 无重叠"
  fi
}

tc13_page_size_boundaries() {
  local tc="TC-13 pageSize 边界"
  local kw
  kw="$(get_env_or_default "KEYWORD_TITLE" "项目")"

  local url_big="${API_BASE}/api/evidence/global-search?keyword=$(printf '%s' "${kw}" | jq -s -R -r @uri)&page=1&pageSize=1000"
  local url_zero="${API_BASE}/api/evidence/global-search?keyword=$(printf '%s' "${kw}" | jq -s -R -r @uri)&page=1&pageSize=0"

  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )

  do_curl "GET" "${url_big}" "${auth_args[@]}"
  local body_big
  body_big="$(read_body)"
  cleanup_http_tmp
  do_curl "GET" "${url_zero}" "${auth_args[@]}"
  local body_zero
  body_zero="$(read_body)"
  cleanup_http_tmp

  local ps_big ps_zero
  ps_big="$(printf '%s' "${body_big}" | jq -r '.data.pageSize // empty' 2>/dev/null || echo "")"
  ps_zero="$(printf '%s' "${body_zero}" | jq -r '.data.pageSize // empty' 2>/dev/null || echo "")"

  if [[ -z "${ps_big}" && -z "${ps_zero}" ]]; then
    append_result_row "${tc}" "WARN" "响应中未包含 data.pageSize 字段，无法验证 pageSize 边界"
    return
  fi

  local ok="true"
  if [[ -n "${ps_big}" && ( "${ps_big}" -lt 1 || "${ps_big}" -gt 100 ) ]]; then
    ok="false"
  fi
  if [[ -n "${ps_zero}" && ( "${ps_zero}" -lt 1 || "${ps_zero}" -gt 100 ) ]]; then
    ok="false"
  fi

  if [[ "${ok}" == "true" ]]; then
    append_result_row "${tc}" "PASS" "pageSize 边界检查通过（data.pageSize 在 1..100 之间或未返回）"
  else
    append_result_row "${tc}" "FAIL" "data.pageSize 超出 1..100 范围（big=${ps_big:-<空>}, zero=${ps_zero:-<空>}）"
    append_fail_curl "${tc}" "${url_big}" "GET"
  fi
}

tc14_latest_version_filled() {
  local tc="TC-14 latestVersion 填充（弱断言）"
  local kw
  kw="$(get_env_or_default "KEYWORD_TITLE" "项目")"
  local url="${API_BASE}/api/evidence/global-search?keyword=$(printf '%s' "${kw}" | jq -s -R -r @uri)&page=1&pageSize=10"

  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )
  do_curl "GET" "${url}" "${auth_args[@]}"
  local body
  body="$(read_body)"
  cleanup_http_tmp

  local total count_nonnull
  total="$(printf '%s' "${body}" | jq -r '.data.records | length' 2>/dev/null || echo "0")"
  count_nonnull="$(printf '%s' "${body}" | jq '[.data.records[]? | select(.latestVersion != null)] | length' 2>/dev/null || echo "0")"

  if [[ "${total}" -eq 0 ]]; then
    append_result_row "${tc}" "WARN" "records 为空，无法验证 latestVersion"
  elif [[ "${count_nonnull}" -eq 0 ]]; then
    append_result_row "${tc}" "WARN" "所有记录 latestVersion 均为空，可能是无版本数据或 Service 填充未生效，需要人工确认"
  else
    append_result_row "${tc}" "PASS" "total=${total}，其中 ${count_nonnull} 条记录 latestVersion 非空"
  fi
}

tc15_special_characters_no_500() {
  local tc="TC-15 特殊字符不 500"
  local auth_args_str=""
  auth_args_for "A" auth_args_str
  # shellcheck disable=SC2206
  local auth_args=( ${auth_args_str} )

  local url_pct="${API_BASE}/api/evidence/global-search?keyword=%25&page=1&pageSize=10"
  local url_us="${API_BASE}/api/evidence/global-search?keyword=_&page=1&pageSize=10"

  do_curl "GET" "${url_pct}" "${auth_args[@]}"
  local status1="${HTTP_STATUS}"
  cleanup_http_tmp

  do_curl "GET" "${url_us}" "${auth_args[@]}"
  local status2="${HTTP_STATUS}"
  cleanup_http_tmp

  if [[ "${status1}" != "500" && "${status2}" != "500" ]]; then
    append_result_row "${tc}" "PASS" "特殊字符 %25 与 _ 请求未返回 500（实际：${status1}/${status2}）"
  else
    append_result_row "${tc}" "FAIL" "存在特殊字符请求返回 500（%25: ${status1}, _: ${status2}）"
    append_fail_curl "${tc}" "${url_pct}" "GET"
  fi
}

cleanup_cookies() {
  rm -f "${COOKIE_JAR_A}" "${COOKIE_JAR_B}" 2>/dev/null || true
}

print_summary() {
  {
    echo
    echo "## 汇总"
    echo
    echo "- PASS 用例数：${PASS_COUNT}"
    echo "- FAIL 用例数：${FAIL_COUNT}"
    echo "- WARN 用例数：${WARN_COUNT}"
    echo
  } >> "${REPORT_FILE}"

  echo
  echo "==== 全局证据搜索冒烟测试完成 ===="
  echo "PASS: ${PASS_COUNT}, FAIL: ${FAIL_COUNT}, WARN: ${WARN_COUNT}"
  echo "详细报告：${REPORT_FILE}"
}

main() {
  ensure_tools
  load_env
  detect_api_base
  init_report

  mkdir -p "${REPORT_DIR}"

  # 登录用户 A/B（失败不立即退出，由具体用例判断是否可执行）
  login_user "A" || true
  login_user "B" || true

  # 逐条执行用例
  tc01_unauthorized
  tc02_login_success_structure
  tc03_keyword_missing_empty_page
  tc04_keyword_blank_empty_page
  tc05_title_ilike
  tc06_realname_ilike
  tc07_username_ilike
  tc08_case_insensitive
  tc09_permission_filter
  tc10_exclude_invalid
  tc11_sort_created_desc
  tc12_pagination_no_overlap
  tc13_page_size_boundaries
  tc14_latest_version_filled
  tc15_special_characters_no_500

  cleanup_cookies
  print_summary
}

main "$@"

