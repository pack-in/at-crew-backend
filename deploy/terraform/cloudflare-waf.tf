# PA-11 — Cloudflare WAF 관리형 룰(설계 문서 D9). AWS WAF 대신 이미 쓰는 Cloudflare에서 켠다.
#
# ⚠️ 이 리소스는 apply하는 즉시 지금 실제로 서비스 중인 api.at-crew.com 트래픽에 바로 적용된다
# (Tunnel/Route53과 달리 "만들어만 두고 나중에 연결"하는 게 아니라 존 전체에 즉시 반영됨).
# 오탐으로 정상 요청이 차단될 가능성이 낮지만 0은 아니다 — apply 직후 최소 몇 시간은 Sentry
# 5xx·Cloudflare Security Events를 지켜볼 사람이 있을 때 적용할 것(PLAN-HUMAN PH-10 원래 계획대로).
#
# OWASP Core Ruleset은 이번에 포함하지 않았다 — Cloudflare Managed Ruleset보다 오탐률이 높은
# 편이라, 최소한의 검증된 베이스라인부터 켜고 안정성 확인 후 추가하는 게 안전하다.
resource "cloudflare_ruleset" "waf_managed" {
  count = var.waf_enabled ? 1 : 0

  zone_id     = var.cloudflare_zone_id
  name        = "at-crew WAF managed rules"
  description = "Cloudflare Managed Ruleset - PA-11"
  kind        = "zone"
  phase       = "http_request_firewall_managed"

  rules {
    action = "execute"
    action_parameters {
      # Cloudflare Managed Ruleset — 전 계정 공통 고정 ID(Cloudflare 문서 기준, 계정마다 다르지 않음)
      id = "efb7b8c949ac4650a09736fc376e9aee"
    }
    expression  = "true"
    description = "Cloudflare Managed Ruleset 전체 활성화"
    enabled     = true
  }
}
