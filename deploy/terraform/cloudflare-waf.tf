# PA-11 — Cloudflare WAF 관리형 룰(설계 문서 D9). AWS WAF 대신 이미 쓰는 Cloudflare에서 켠다.
#
# ⚠️ 이 리소스는 apply하는 즉시 지금 실제로 서비스 중인 api.at-crew.com 트래픽에 바로 적용된다
# (Tunnel/Route53과 달리 "만들어만 두고 나중에 연결"하는 게 아니라 존 전체에 즉시 반영됨).
# 오탐으로 정상 요청이 차단될 가능성이 낮지만 0은 아니다 — apply 직후 최소 몇 시간은 Sentry
# 5xx·Cloudflare Security Events를 지켜볼 사람이 있을 때 적용할 것(PLAN-HUMAN PH-10 원래 계획대로).
#
# OWASP Core Ruleset은 이번에 포함하지 않았다 — Cloudflare Managed Ruleset보다 오탐률이 높은
# 편이라, 최소한의 검증된 베이스라인부터 켜고 안정성 확인 후 추가하는 게 안전하다.
#
# 2026-09-03 정정: 원래 쓴 룰셋 ID(efb7b8c949ac4650a09736fc376e9aee, 유료 Managed Ruleset)는
# Free 플랜에 entitle되지 않아 apply가 실패했다(terraform apply 에러로 확인). 그 뒤 Cloudflare
# 대시보드에서 직접 "Cloudflare Managed **Free** Ruleset"이 켜진 걸 발견 — Free 플랜에서도 쓸 수
# 있는 축소판이다. 실제 라이브 상태(id=77454fe2d30c4220b5701f6fdfb893ba)에 코드를 맞춘다.
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
      # Cloudflare Managed Free Ruleset — Free 플랜에서 entitle되는 축소판(전 계정 공통 고정 ID).
      id = "77454fe2d30c4220b5701f6fdfb893ba"
    }
    expression  = "true"
    description = "Cloudflare Managed Free Ruleset 활성화"
    enabled     = true
  }
}
