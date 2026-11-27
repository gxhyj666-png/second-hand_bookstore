#!/usr/bin/env bash
# 同窗书 · 后端冒烟测试（macOS / Linux 版）
# 用法：后端在 8088 端口运行时执行  bash tools/smoke.sh
# 依赖：curl + python3（用于解析 JSON）
set -uo pipefail

BASE="${BASE_URL:-http://127.0.0.1:8088}"
PASS=0
FAIL=0

json_get() { python3 -c "import sys,json;d=json.load(sys.stdin);
p='$1'.split('.')
for k in p: d=d[int(k)] if k.isdigit() else d.get(k)
print('' if d is None else d)"; }

api() { # method path token body
  local method="$1" path="$2" token="${3:-}" body="${4:-}"
  local args=(-s -X "$method" "$BASE$path" -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  curl "${args[@]}"
}

step() { # name, fn
  if "$2"; then
    echo "  [通过] $1"; PASS=$((PASS+1))
  else
    echo "  [失败] $1"; FAIL=$((FAIL+1))
  fi
}

echo ""
echo "== 同窗书后端冒烟测试 @ $BASE =="

TOKEN_BUYER=""; TOKEN_CLERK=""; ORDER_NO=""; VERIFY_CODE=""; LISTING_ID=""; TEXTBOOK_ID=""

step "健康检查" health_ok() {
  local r; r=$(api GET /v1/health)
  [ "$(echo "$r" | json_get data.status)" = "UP" ]
}

step "买家登录" login_buyer() {
  local r; r=$(api POST /v1/auth/login "" '{"account":"13800000002","password":"123456"}')
  TOKEN_BUYER=$(echo "$r" | json_get data.token)
  [ -n "$TOKEN_BUYER" ]
}

step "店员登录" login_clerk() {
  local r; r=$(api POST /v1/auth/login "" '{"account":"13800000004","password":"123456"}')
  TOKEN_CLERK=$(echo "$r" | json_get data.token)
  [ -n "$TOKEN_CLERK" ]
}

step "管理员看板" admin_dashboard() {
  local tok r; tok=$(api POST /v1/auth/login "" '{"account":"13800000001","password":"admin123"}' | json_get data.token)
  r=$(api GET /v1/admin/dashboard "$tok")
  [ "$(echo "$r" | json_get data.userCount)" -ge 5 ]
}

step "检索在售商品" list_listings() {
  local r; r=$(api GET '/v1/listings?size=5&sort=newest' "$TOKEN_BUYER")
  LISTING_ID=$(echo "$r" | json_get data.records.0.id)
  TEXTBOOK_ID=$(echo "$r" | json_get data.records.0.textbookId)
  [ -n "$LISTING_ID" ]
}

step "商品详情" listing_detail() {
  local r; r=$(api GET "/v1/listings/$LISTING_ID" "$TOKEN_BUYER")
  [ "$(echo "$r" | json_get code)" = "0" ]
}

step "价格指数" price_index() {
  local r; r=$(api GET "/v1/textbooks/$TEXTBOOK_ID/price-index" "$TOKEN_BUYER")
  [ "$(echo "$r" | json_get code)" = "0" ]
}

step "推荐列表" recommend() {
  local r; r=$(api GET '/v1/recommend?limit=5' "$TOKEN_BUYER")
  [ "$(echo "$r" | json_get code)" = "0" ]
}

step "下单（含幂等）" create_order() {
  local rid r1 r2 point
  rid=$(python3 -c "import uuid;print(uuid.uuid4())")
  point=$(api GET '/v1/pickup-points?schoolId=1' "$TOKEN_BUYER" | json_get data.0.id)
  r1=$(api POST /v1/orders "$TOKEN_BUYER" "{\"listingId\":$LISTING_ID,\"pointId\":$point,\"requestId\":\"$rid\"}")
  ORDER_NO=$(echo "$r1" | json_get data.orderNo)
  r2=$(api POST /v1/orders "$TOKEN_BUYER" "{\"listingId\":$LISTING_ID,\"pointId\":$point,\"requestId\":\"$rid\"}")
  [ -n "$ORDER_NO" ] && [ "$ORDER_NO" = "$(echo "$r2" | json_get data.orderNo)" ]
}

step "支付（资金冻结）" pay_order() {
  local r; r=$(api POST "/v1/orders/$ORDER_NO/pay" "$TOKEN_BUYER" '{"channel":"MOCK"}')
  [ "$(echo "$r" | json_get data.status)" = "FROZEN" ]
}

step "进入待核销" wait_verify() {
  local r; r=$(api POST "/v1/orders/$ORDER_NO/wait-verify" "$TOKEN_BUYER")
  [ "$(echo "$r" | json_get data.status)" = "WAIT_VERIFY" ]
}

step "获取动态核销码" get_code() {
  local r; r=$(api GET "/v1/orders/$ORDER_NO/verify-code" "$TOKEN_BUYER")
  VERIFY_CODE=$(echo "$r" | json_get data.code)
  [ -n "$VERIFY_CODE" ]
}

step "店员核销" verify_order() {
  local r; r=$(api POST /v1/orders/verify "$TOKEN_CLERK" "{\"orderNo\":\"$ORDER_NO\",\"code\":\"$VERIFY_CODE\",\"method\":\"SCAN\"}")
  [ "$(echo "$r" | json_get data.status)" = "SETTLED" ]
}

echo ""
echo "== 结果：通过 $PASS，失败 $FAIL =="
[ "$FAIL" -eq 0 ]
