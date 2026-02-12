#!/usr/bin/env bash
# Phase 4 接口验收脚本（curl）
# 使用前：先登录并将 Cookie 写入 scripts/.cookie，或设置 BASE_URL / PROJECT_ID / COOKIE_FILE
# 示例：curl -c scripts/.cookie -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"xxx"}'

set -e
BASE_URL="${BASE_URL:-http://localhost:8081/api}"
PROJECT_ID="${PROJECT_ID:-1}"
STAGE_CODE="${STAGE_CODE:-S1}"
EVIDENCE_TYPE_CODE="${EVIDENCE_TYPE_CODE:-S1_START_PHOTO}"
COOKIE_FILE="${COOKIE_FILE:-$(dirname "$0")/.cookie}"
CURL_OPTS=(-s -w "\n%{http_code}" -H "Content-Type: application/json")
[[ -f "$COOKIE_FILE" ]] && CURL_OPTS+=(-b "$COOKIE_FILE")

echo "=== 1. GET /api/projects (列表扩展 evidenceCompletionPercent, keyMissingSummary) ==="
resp=$(curl "${CURL_OPTS[@]}" "$BASE_URL/projects")
body=$(echo "$resp" | head -n -1)
code=$(echo "$resp" | tail -n 1)
echo "HTTP $code"
echo "$body" | jq -e '.code == 0' >/dev/null && echo "  [OK] code==0"
echo "$body" | jq -e '.data | type == "array"' >/dev/null && echo "  [OK] data is array"
echo "$body" | jq -e '.data[0] | has("evidenceCompletionPercent")' >/dev/null 2>/dev/null && echo "  [OK] evidenceCompletionPercent present" || echo "  [SKIP] no projects or field missing"
echo "$body" | jq -e '.data[0] | has("keyMissingSummary")' >/dev/null 2>/dev/null && echo "  [OK] keyMissingSummary present" || echo "  [SKIP] no projects or field missing"

echo ""
echo "=== 2. GET /api/projects/{id}/stage-progress ==="
resp=$(curl "${CURL_OPTS[@]}" "$BASE_URL/projects/$PROJECT_ID/stage-progress")
body=$(echo "$resp" | head -n -1)
code=$(echo "$resp" | tail -n 1)
echo "HTTP $code"
echo "$body" | jq -e '.code == 0' >/dev/null && echo "  [OK] code==0"
echo "$body" | jq -e '.data | has("overallCompletionPercent") and has("keyMissing") and has("canArchive") and has("stages")' >/dev/null && echo "  [OK] stage-progress fields present"
echo "$body" | jq -e '.data.stages | type == "array"' >/dev/null && echo "  [OK] stages is array"
echo "$body" | jq -e '.data | has("blockedByStages") and has("blockedByRequiredItems")' >/dev/null && echo "  [OK] blockedBy* fields present"

echo ""
echo "=== 3. GET /api/projects/{id}/stages/{stageCode}/evidence-types/{evidenceTypeCode}/evidences ==="
resp=$(curl "${CURL_OPTS[@]}" "$BASE_URL/projects/$PROJECT_ID/stages/$STAGE_CODE/evidence-types/$EVIDENCE_TYPE_CODE/evidences")
body=$(echo "$resp" | head -n -1)
code=$(echo "$resp" | tail -n 1)
echo "HTTP $code"
echo "$body" | jq -e '.code == 0' >/dev/null && echo "  [OK] code==0"
echo "$body" | jq -e '.data | type == "array"' >/dev/null && echo "  [OK] data is array (evidence list)"

echo ""
echo "=== 4. POST /api/projects/{id}/stages/{stageCode}/complete (门禁失败时 400 + data.missingItems) ==="
resp=$(curl "${CURL_OPTS[@]}" -X POST "$BASE_URL/projects/$PROJECT_ID/stages/$STAGE_CODE/complete")
body=$(echo "$resp" | head -n -1)
code=$(echo "$resp" | tail -n 1)
echo "HTTP $code"
echo "$body" | jq -e 'if .code == 0 then .data.success == true else .code == 400 and (.data | has("message") and has("missingItems")) end' >/dev/null && echo "  [OK] success=>data.success; fail=>code 400, data.message, data.missingItems"

echo ""
echo "=== 5. POST /api/projects/{id}/archive (门禁失败时 400 + data.archiveBlockReason/keyMissing/blockedBy*) ==="
resp=$(curl "${CURL_OPTS[@]}" -X POST "$BASE_URL/projects/$PROJECT_ID/archive")
body=$(echo "$resp" | head -n -1)
code=$(echo "$resp" | tail -n 1)
echo "HTTP $code"
echo "$body" | jq -e 'if .code == 0 then true else .code == 400 and (.data | has("archiveBlockReason") and has("keyMissing") and has("blockedByStages") and has("blockedByRequiredItems")) end' >/dev/null && echo "  [OK] success=>code 0; fail=>code 400, data has archiveBlockReason/keyMissing/blockedByStages/blockedByRequiredItems"

echo ""
echo "=== Phase 4 接口验收脚本执行完毕 ==="
