# 루트 도메인 at-crew.com의 DMARC 정책.
#
# 서비스 메일은 mail.at-crew.com에서 보내고(Cloudflare Email Service), 그쪽 DMARC(_dmarc.mail.at-crew.com,
# p=reject)는 Email Sending 활성화 때 만들어져 있다. 루트 도메인에는 DMARC도 SPF도 없어 누구나
# @at-crew.com을 발신자로 사칭할 수 있었다. 루트에서 메일을 보내는 곳이 없으므로 같은 정책(reject)으로 막는다.
# 루트에서 메일을 보내기 시작하면(예: Gmail "다른 주소로 보내기") 그 경로의 SPF·DKIM부터 맞춘 뒤 바꿔야 한다.
resource "cloudflare_record" "dmarc_root" {
  zone_id = var.cloudflare_zone_id
  name    = "_dmarc"
  type    = "TXT"
  content = "v=DMARC1; p=reject;"
  ttl     = 1 # 자동
  comment = "루트 도메인 사칭 방지 — 메일 발송은 mail.at-crew.com만 한다"
}
