# PA-11 — Cloudflare Tunnel(설계 문서 D4). ALB 없이 인바운드 자체를 없애는 핵심 컴포넌트.
#
# 지금 이 리소스만 만들어서는 트래픽에 아무 영향이 없다 — cloudflared가 실제 앱 인스턴스에
# 설치·기동되고(PA-06) DNS가 이 Tunnel을 가리키도록 바뀌기 전까지(PH-07 blue-green 전환)는
# 완전히 비활성 상태로만 존재한다. Tunnel/WAF 중 오늘 먼저 만들어도 안전한 쪽은 이거다.
resource "random_id" "tunnel_secret" {
  byte_length = 35
}

resource "cloudflare_zero_trust_tunnel_cloudflared" "app" {
  account_id = var.cloudflare_account_id
  name       = "at-crew-app"
  secret     = random_id.tunnel_secret.b64_std
}

output "cloudflare_tunnel_id" {
  value = cloudflare_zero_trust_tunnel_cloudflared.app.id
}

output "cloudflare_tunnel_cname" {
  description = "PH-07에서 DNS를 이 값으로 돌린다(CNAME)"
  value       = cloudflare_zero_trust_tunnel_cloudflared.app.cname
}

output "cloudflare_tunnel_token" {
  description = "cloudflared service install <token>에 쓴다(PA-06). sensitive라 -raw로만 조회할 것, 화면에 그냥 안 찍힘"
  value       = cloudflare_zero_trust_tunnel_cloudflared.app.tunnel_token
  sensitive   = true
}
