#!/usr/bin/env bash
# API Quality Guard - CI 质量门禁脚本
# 用法: ./quality-gate.sh <planId> [baseUrl]
# 流程: 触发计划执行 → 轮询任务直到终态 → 调用门禁检查 → 通过 exit 0 / 阻断 exit 1
# 依赖: curl、jq
set -euo pipefail

PLAN_ID="${1:?用法: quality-gate.sh <planId> [baseUrl]}"
BASE_URL="${2:-http://localhost:8080}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-300}"
AQG_USERNAME="${AQG_USERNAME:-}"
AQG_PASSWORD="${AQG_PASSWORD:-}"

if [ -z "${AQG_USERNAME}" ] || [ -z "${AQG_PASSWORD}" ]; then
  echo "需要设置 AQG_USERNAME 和 AQG_PASSWORD（平台登录账号）" >&2
  exit 2
fi

echo "==> 登录平台账号 ${AQG_USERNAME}"
LOGIN_BODY=$(jq -n --arg username "${AQG_USERNAME}" --arg password "${AQG_PASSWORD}" \
  '{username: $username, password: $password}')
LOGIN_RESPONSE=$(curl -sS -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" -d "${LOGIN_BODY}")
TOKEN=$(echo "${LOGIN_RESPONSE}" | jq -r '.data.token // empty' 2>/dev/null || true)
if [ -z "${TOKEN}" ]; then
  LOGIN_MESSAGE=$(echo "${LOGIN_RESPONSE}" | jq -r '.message // "未知错误"' 2>/dev/null || echo "响应无法解析")
  echo "登录失败：${LOGIN_MESSAGE}" >&2
  exit 2
fi
AUTH_HEADER="Authorization: Bearer ${TOKEN}"

echo "==> 触发计划 #${PLAN_ID} 执行（${BASE_URL}）"
RUN_ID=$(curl -fsS -X POST "${BASE_URL}/api/plans/${PLAN_ID}/runs" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" -d '{}' | jq -r '.data.runId')
echo "    任务 ID: ${RUN_ID}"

echo "==> 轮询任务状态（超时 ${MAX_WAIT_SECONDS}s）"
elapsed=0
while true; do
  STATUS=$(curl -fsS -H "${AUTH_HEADER}" "${BASE_URL}/api/runs/${RUN_ID}" | jq -r '.data.status')
  echo "    [${elapsed}s] ${STATUS}"
  case "${STATUS}" in
    SUCCESS | FAILED) break ;;
    PENDING | RUNNING) ;;
    *)
      echo "任务未能正常结束（${STATUS}），CI 终止" >&2
      exit 2
      ;;
  esac
  if [ "${elapsed}" -ge "${MAX_WAIT_SECONDS}" ]; then
    echo "等待任务超时（${MAX_WAIT_SECONDS}s）" >&2
    exit 2
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done

echo "==> 执行质量门禁检查"
GATE=$(curl -fsS -X POST "${BASE_URL}/api/gates/check" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -d "{\"runId\": ${RUN_ID}}")

PASSED=$(echo "${GATE}" | jq -r '.data.passed')
SCORE=$(echo "${GATE}" | jq -r '.data.score')
echo "    评分: ${SCORE}    结论: $([ "${PASSED}" = "true" ] && echo 通过 || echo 阻断)"

if [ "${PASSED}" != "true" ]; then
  echo "==> 门禁违规明细:"
  echo "${GATE}" | jq -r '.data.violations[] | "    - \(.rule): \(.detail)"'
  exit 1
fi

echo "==> 质量门禁通过，允许发布"
