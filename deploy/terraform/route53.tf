# PA-10 / 설계 문서 D8 — DB 엔드포인트를 Route53 Private Hosted Zone으로 추상화한다.
# 지금은 at-crew-be IAM 사용자에게 route53:* 권한이 전혀 없어(ListHostedZones부터 AccessDenied)
# var.route53_enabled 기본값을 false로 두고 count로 리소스 생성을 건너뛴다.
# PLAN-HUMAN PH-01에서 권한을 확보하면 -var="route53_enabled=true"로 적용한다.

resource "aws_route53_zone" "internal" {
  count = var.route53_enabled ? 1 : 0

  name = "internal.at-crew.com"

  vpc {
    vpc_id = aws_vpc.main.id
  }

  tags = {
    Name = "at-crew-internal-zone"
  }
}

resource "aws_route53_record" "db" {
  count = var.route53_enabled ? 1 : 0

  zone_id = aws_route53_zone.internal[0].zone_id
  name    = "db.internal.at-crew.com"
  type    = "A"
  ttl     = 30
  # PH-08에서 실제 MariaDB Primary의 프라이빗 IP로 갱신, 승격 시(PA-09 런북) 이 레코드만 바꾼다.
  records = ["10.20.1.10"]
}
