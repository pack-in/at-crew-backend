# PH-07 blue-green 검증용 — cloudflared가 어느 호스트명 요청을 로컬 어디로 보낼지 정의한다.
# 지금은 스테이징(임시 EC2, PH-07 드라이런) 검증 단계라 실제 api.at-crew.com이 아니라
# 테스트 전용 서브도메인을 쓴다. 실제 cutover 시 이 파일의 hostname을 api.at-crew.com으로
# 바꾸고, 기존 A레코드(EC2#1 Elastic IP)를 대체한다.
resource "cloudflare_zero_trust_tunnel_cloudflared_config" "app" {
  account_id = var.cloudflare_account_id
  tunnel_id  = cloudflare_zero_trust_tunnel_cloudflared.app.id

  config {
    # PH-07 실제 cutover(2026-09-02) — api.at-crew.com을 라우팅에 추가. DNS는 아직 안 바꿨다,
    # 이 규칙은 Tunnel이 그 호스트명 요청을 받을 준비만 해두는 것.
    ingress_rule {
      hostname = "api.at-crew.com"
      service  = "http://localhost:80"
    }
    ingress_rule {
      hostname = "staging-dryrun.at-crew.com"
      service  = "http://localhost:80"
    }
    # 매칭 안 되는 나머지 요청은 404 — catch-all은 항상 마지막에 있어야 한다.
    ingress_rule {
      service = "http_status:404"
    }
  }
}

resource "cloudflare_record" "staging_dryrun" {
  zone_id = var.cloudflare_zone_id
  name    = "staging-dryrun"
  type    = "CNAME"
  content = cloudflare_zero_trust_tunnel_cloudflared.app.cname
  proxied = true
}
