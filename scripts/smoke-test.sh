#!/usr/bin/env bash
set -euo pipefail

base_url="${GLINT_BASE_URL:-http://127.0.0.1:8080}"
smoke_phone_a="${GLINT_SMOKE_PHONE_A:-13800138000}"
smoke_phone_b="${GLINT_SMOKE_PHONE_B:-13800138001}"
smoke_sms_code="${GLINT_SMOKE_SMS_CODE:-000000}"
smoke_dir="$(mktemp -d /private/tmp/glint-smoke.XXXXXX)"
trap 'rm -rf "$smoke_dir"' EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "缺少命令: $1" >&2; exit 1; }
}

request() {
  local method="$1" path="$2" token="$3" body="$4" output="$5"
  local headers=(-H "Accept: application/json")
  if [[ -n "$token" ]]; then headers+=(-H "Authorization: Bearer $token"); fi
  if [[ -n "$body" ]]; then headers+=(-H "Content-Type: application/json"); fi
  if [[ -n "$body" ]]; then
    curl --fail-with-body --noproxy '*' -sS -X "$method" "${base_url}${path}" "${headers[@]}" --data "$body" -o "$output"
  else
    curl --fail-with-body --noproxy '*' -sS -X "$method" "${base_url}${path}" "${headers[@]}" -o "$output"
  fi
  jq -e '.code == "OK"' "$output" >/dev/null
}

request_error() {
  local expected_status="$1" expected_code="$2" method="$3" path="$4" token="$5" body="$6" output="$7"
  local headers=(-H "Accept: application/json") status
  if [[ -n "$token" ]]; then headers+=(-H "Authorization: Bearer $token"); fi
  if [[ -n "$body" ]]; then headers+=(-H "Content-Type: application/json"); fi
  if [[ -n "$body" ]]; then
    status="$(curl --noproxy '*' -sS -X "$method" "${base_url}${path}" "${headers[@]}" --data "$body" -o "$output" -w '%{http_code}')"
  else
    status="$(curl --noproxy '*' -sS -X "$method" "${base_url}${path}" "${headers[@]}" -o "$output" -w '%{http_code}')"
  fi
  [[ "$status" == "$expected_status" ]] || { echo "期望 HTTP $expected_status，实际为 $status: $path" >&2; exit 1; }
  jq -e --arg code "$expected_code" '.code == $code and (.traceId | length > 0)' "$output" >/dev/null
}

require_command curl
require_command jq

request GET "/api/v1/system/status" "" "" "$smoke_dir/status.json"
jq -e '.data.status == "UP" and .data.database == "UP" and .data.redis == "UP" and .data.schemaVersion == .data.expectedSchemaVersion' "$smoke_dir/status.json" >/dev/null
request_error 404 NOT_FOUND GET "/api/v1/not-a-real-endpoint" "" "" "$smoke_dir/not-found.json"

request POST "/api/v1/auth/sessions" "" '{}' "$smoke_dir/session-a.json"
request POST "/api/v1/auth/sessions" "" '{}' "$smoke_dir/session-b.json"
token_a="$(jq -r '.data.sessionToken' "$smoke_dir/session-a.json")"
token_b="$(jq -r '.data.sessionToken' "$smoke_dir/session-b.json")"

guest_summary='{"cityCode":"TEST_CITY","lineId":"TEST_LINE","stationCode":"TEST_STATION","stationName":"联调测试站","occurredAt":"2026-08-26T00:00:00Z","statusSummary":"searching","directionConfidence":0}'
request_error 403 ACCOUNT_REQUIRED POST "/api/v1/commutes/station-summaries" "$token_a" "$guest_summary" "$smoke_dir/guest-commute.json"

request POST "/api/v1/auth/sms/send" "$token_a" "$(jq -nc --arg phone "$smoke_phone_a" '{phone:$phone}')" "$smoke_dir/sms-send-a.json"
request POST "/api/v1/auth/sms/login" "$token_a" "$(jq -nc --arg phone "$smoke_phone_a" --arg code "$smoke_sms_code" '{phone:$phone,code:$code}')" "$smoke_dir/sms-login-a.json"
request POST "/api/v1/auth/sms/send" "$token_b" "$(jq -nc --arg phone "$smoke_phone_b" '{phone:$phone}')" "$smoke_dir/sms-send-b.json"
request POST "/api/v1/auth/sms/login" "$token_b" "$(jq -nc --arg phone "$smoke_phone_b" --arg code "$smoke_sms_code" '{phone:$phone,code:$code}')" "$smoke_dir/sms-login-b.json"
token_a="$(jq -r '.data.sessionToken' "$smoke_dir/sms-login-a.json")"
token_b="$(jq -r '.data.sessionToken' "$smoke_dir/sms-login-b.json")"

request PUT "/api/v1/profiles/me" "$token_a" \
  '{"avatarKey":"aurora","interestTags":["音乐","阅读"],"nickname":"联调甲","regionCode":"TEST","regionName":"联调测试市"}' \
  "$smoke_dir/profile-a.json"
request PUT "/api/v1/profiles/me" "$token_b" \
  '{"avatarKey":"mint","interestTags":["音乐","摄影"],"nickname":"联调乙","regionCode":"TEST","regionName":"联调测试市"}' \
  "$smoke_dir/profile-b.json"

preference='{"startTime":"07:30","endTime":"09:30","preferredLineIds":["TEST_LINE"],"autoMatch":true,"notificationsEnabled":false,"stealth":false,"favoriteStationCode":"TEST_STATION","favoriteStationName":"联调测试站"}'
request PUT "/api/v1/commute-preferences/me" "$token_a" "$preference" "$smoke_dir/preference-a.json"
request PUT "/api/v1/commute-preferences/me" "$token_b" "$preference" "$smoke_dir/preference-b.json"
request PUT "/api/v1/profiles/me/commute-status" "$token_a" '{"status":"searching"}' "$smoke_dir/status-a-searching.json"
request PUT "/api/v1/profiles/me/commute-status" "$token_b" '{"status":"searching"}' "$smoke_dir/status-b-searching.json"

occurred_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
summary="{\"cityCode\":\"TEST_CITY\",\"lineId\":\"TEST_LINE\",\"stationCode\":\"TEST_STATION\",\"stationName\":\"联调测试站\",\"occurredAt\":\"${occurred_at}\",\"statusSummary\":\"searching\",\"directionConfidence\":0}"
request POST "/api/v1/commutes/station-summaries" "$token_a" "$summary" "$smoke_dir/summary-a.json"
request POST "/api/v1/commutes/station-summaries" "$token_b" "$summary" "$smoke_dir/summary-b.json"

request GET "/api/v1/matches/candidates" "$token_a" "" "$smoke_dir/candidates-a.json"
match_id="$(jq -er '.data[0].matchId' "$smoke_dir/candidates-a.json")"
jq -e '.data[0].realInfoRequiredEncounters == 3 and .data[0].realInfoExchangeEligible == false' "$smoke_dir/candidates-a.json" >/dev/null
request_error 400 VALIDATION_ERROR POST "/api/v1/matches/${match_id}/actions" "$token_a" \
  '{"action":"CONFIRM_REAL_INFO_EXCHANGE"}' "$smoke_dir/premature-real-info.json"
request POST "/api/v1/matches/${match_id}/actions" "$token_a" '{"action":"MARK_INTERESTED"}' "$smoke_dir/interested-a.json"
request POST "/api/v1/matches/${match_id}/actions" "$token_b" '{"action":"MARK_INTERESTED"}' "$smoke_dir/interested-b.json"
request POST "/api/v1/matches/${match_id}/unlock" "$token_a" '{"requestedLevel":3,"consent":true,"realInfoExchangeConfirmed":false}' "$smoke_dir/unlock-a.json"
request POST "/api/v1/matches/${match_id}/unlock" "$token_b" '{"requestedLevel":3,"consent":true,"realInfoExchangeConfirmed":false}' "$smoke_dir/unlock-b.json"
session_id="$(jq -er '.data.chatSessionId' "$smoke_dir/unlock-b.json")"

request POST "/api/v1/chat/sessions" "$token_a" "{\"matchId\":\"${match_id}\"}" "$smoke_dir/chat-session.json"
jq -e --arg expected "$session_id" '.data.sessionId == $expected' "$smoke_dir/chat-session.json" >/dev/null
request GET "/api/v1/chat/sessions/${session_id}" "$token_a" "" "$smoke_dir/chat-session-detail.json"
request POST "/api/v1/auth/sessions" "" '{}' "$smoke_dir/session-intruder.json"
token_intruder="$(jq -r '.data.sessionToken' "$smoke_dir/session-intruder.json")"
request_error 403 FORBIDDEN GET "/api/v1/chat/sessions/${session_id}" "$token_intruder" "" "$smoke_dir/chat-forbidden.json"
request POST "/api/v1/chat/sessions/${session_id}/messages" "$token_a" '{"text":"联调消息：只以密文落库"}' "$smoke_dir/message.json"
message_id="$(jq -er '.data.messageId' "$smoke_dir/message.json")"
request GET "/api/v1/chat/sessions/${session_id}/messages" "$token_b" "" "$smoke_dir/messages.json"
jq -e '.data | length >= 1' "$smoke_dir/messages.json" >/dev/null
request POST "/api/v1/safety/message-reports" "$token_b" "{\"messageId\":\"${message_id}\",\"reason\":\"smoke-test\"}" "$smoke_dir/report-message.json"

request POST "/api/v1/board/posts" "$token_a" '{"lineCode":"TEST_LINE","content":"联调测试留言"}' "$smoke_dir/post.json"
post_id="$(jq -er '.data.postId' "$smoke_dir/post.json")"
request POST "/api/v1/board/posts/${post_id}/comments" "$token_b" '{"content":"联调测试评论"}' "$smoke_dir/comment.json"
request GET "/api/v1/board/posts/${post_id}/comments" "$token_a" "" "$smoke_dir/comments.json"
jq -e '.data | length >= 1' "$smoke_dir/comments.json" >/dev/null
request GET "/api/v1/board/posts?lineCode=TEST_LINE" "$token_a" "" "$smoke_dir/posts.json"
request GET "/api/v1/board/posts/online-count?lineCode=TEST_LINE" "$token_a" "" "$smoke_dir/online.json"

request GET "/api/v1/home/summary" "$token_a" "" "$smoke_dir/home.json"
request GET "/api/v1/profiles/me/dashboard" "$token_a" "" "$smoke_dir/dashboard.json"
request GET "/api/v1/achievements" "$token_a" "" "$smoke_dir/achievements.json"
request GET "/api/v1/commute-reports/today" "$token_a" "" "$smoke_dir/report.json"
jq -e '.data.encounterCount >= 1 and .data.sameRouteCount >= 1' "$smoke_dir/report.json" >/dev/null
request PUT "/api/v1/profiles/me/commute-status" "$token_a" '{"status":"idle"}' "$smoke_dir/status-a-idle.json"
request PUT "/api/v1/profiles/me/commute-status" "$token_b" '{"status":"idle"}' "$smoke_dir/status-b-idle.json"

request POST "/api/v1/auth/sessions" "" '{}' "$smoke_dir/session-virtual.json"
token_virtual="$(jq -r '.data.sessionToken' "$smoke_dir/session-virtual.json")"
request GET "/api/v1/matches/virtual-passengers" "$token_virtual" "" "$smoke_dir/virtual.json"
jq -e '.data[0].isVirtual == true and .data[0].automatedReplyCapability == "NOT_CONFIGURED" and .data[0].chatSessionId == null and .data[0].realInfoExchangeEligible == false' "$smoke_dir/virtual.json" >/dev/null

echo "Glint API smoke test passed"
echo "match=${match_id:0:8} session=${session_id:0:8} post=${post_id:0:8}"
