# NAT 인스턴스 SG — Private Subnet에서 오는 트래픽만 통과시킨다.
# AWS 보안그룹 description 필드는 ASCII만 허용해 영문으로 쓴다(HCL 주석은 한국어 유지).
resource "aws_security_group" "nat" {
  name        = "at-crew-nat-sg"
  description = "NAT instance - outbound passthrough for private subnet only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "Traffic from private subnet to be forwarded"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.private_subnet_cidr]
  }

  egress {
    description = "All outbound to internet"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "at-crew-nat-sg"
  }
}

# 앱 서버 SG — 설계 문서 D4: ALB 없이 Cloudflare Tunnel(cloudflared)로만 웹 트래픽 인바운드를 받는다.
# 웹 서비스용 인바운드 규칙은 없다 — cloudflared는 아웃바운드로 연결을 맺으므로 필요 없다.
# self-hosted DB Replica(D6/D12)는 2026-09-03 폐기(docs/design/ha-expansion-path.md로 대체) —
# 그때 열어뒀던 3306 인바운드 규칙(db_replica SG)도 함께 제거했다.
# PH-07(blue-green 전환)에서 실제 앱 인스턴스에 이 SG를 붙인다. 지금은 SG만 미리 만들어 둔다.
resource "aws_security_group" "app" {
  name        = "at-crew-app-sg"
  description = "App server - no web inbound (Cloudflare Tunnel only)"
  vpc_id      = aws_vpc.main.id

  egress {
    description = "Outbound for cloudflared tunnel, image pulls, patching (via NAT)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "at-crew-app-sg"
  }
}
