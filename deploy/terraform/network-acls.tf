# 설계 문서 D10: 보안그룹(인스턴스 단위) + NACL(서브넷 단위) 이중 방어.
# NACL은 stateless라 SG를 대체하지 않는다 — SG 오설정이 나도 서브넷 경계에서 한 번 더 걸러지는
# 용도다. 세밀한 포트별 규칙보다 "이 서브넷 밖에서 들어오는 트래픽 자체를 기본 차단"에 집중한다.

resource "aws_network_acl" "public" {
  vpc_id     = aws_vpc.main.id
  subnet_ids = [aws_subnet.public.id]

  ingress {
    rule_no    = 100
    protocol   = "-1"
    action     = "allow"
    cidr_block = var.vpc_cidr
    from_port  = 0
    to_port    = 0
  }

  ingress {
    rule_no    = 110
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }

  # QA(2026-09-01): protocol "-1"(전체)을 쓰면 from_port/to_port가 무시돼 포트 제한 없이
  # 전체 허용이 돼버린다(AWS NACL 사양) — 그래서 TCP/UDP를 분리한다. DNS·NTP 같은 UDP 응답
  # 트래픽이 없으면 NAT 인스턴스 자신의 이름 해석조차 막혀 dnf/cloudflared가 죽는다.
  ingress {
    rule_no    = 120
    protocol   = "udp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }

  egress {
    rule_no    = 100
    protocol   = "-1"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 0
    to_port    = 0
  }

  tags = {
    Name = "at-crew-public-nacl"
  }
}

resource "aws_network_acl" "private" {
  vpc_id     = aws_vpc.main.id
  subnet_ids = [aws_subnet.private.id]

  ingress {
    rule_no    = 100
    protocol   = "-1"
    action     = "allow"
    cidr_block = var.vpc_cidr
    from_port  = 0
    to_port    = 0
  }

  ingress {
    rule_no    = 110
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }

  # QA(2026-09-01): protocol "-1"(전체)을 쓰면 from_port/to_port가 무시돼 포트 제한 없이
  # 전체 허용이 돼버린다(AWS NACL 사양) — 그래서 TCP/UDP를 분리한다. DNS·NTP 같은 UDP 응답
  # 트래픽이 없으면 NAT 인스턴스 자신의 이름 해석조차 막혀 dnf/cloudflared가 죽는다.
  ingress {
    rule_no    = 120
    protocol   = "udp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }

  egress {
    rule_no    = 100
    protocol   = "-1"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 0
    to_port    = 0
  }

  tags = {
    Name = "at-crew-private-nacl"
  }
}
