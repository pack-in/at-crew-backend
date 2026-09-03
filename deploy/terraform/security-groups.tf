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
# D12(2026-09-02)로 MariaDB Primary가 이 인스턴스에 같이 있어, Replica가 binlog를 당겨갈 수 있게
# db_replica SG에서 오는 3306만 예외로 허용한다(별도 aws_vpc_security_group_ingress_rule로 분리 —
# app/db_replica 두 SG가 서로를 참조해 인라인 ingress로 두면 Terraform이 순환 의존으로 apply 실패함).
# PH-07(blue-green 전환)에서 실제 앱 인스턴스에 이 SG를 붙인다. 지금은 SG만 미리 만들어 둔다.
resource "aws_security_group" "app" {
  name        = "at-crew-app-sg"
  description = "App server - no web inbound (Cloudflare Tunnel only), 3306 from db_replica SG for replication (D4/D12)"
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

# DB Replica SG — Replica 인스턴스 자체를 보호한다.
# QA(2026-09-02): 앱↔Replica 두 SG가 서로를 참조하므로, 순환 의존을 피하기 위해 인바운드 규칙은
# 아래 aws_vpc_security_group_ingress_rule 2개로 분리했다(SG 리소스 자체엔 인라인 ingress 없음).
resource "aws_security_group" "db_replica" {
  name        = "at-crew-db-replica-sg"
  description = "MariaDB replica instance"
  vpc_id      = aws_vpc.main.id

  egress {
    description = "Outbound for patching etc. (via NAT)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "at-crew-db-replica-sg"
  }
}

# Replica → Primary: 복제는 Replica가 Primary에 접속해 binlog를 당겨오는 구조라 이 방향이 맞다.
resource "aws_vpc_security_group_ingress_rule" "app_from_db_replica" {
  security_group_id            = aws_security_group.app.id
  description                  = "MariaDB replication pull from Replica instance only"
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
  referenced_security_group_id = aws_security_group.db_replica.id
}

# 앱 인스턴스 → Replica: replica-lag-check.sh가 앱 인스턴스에서 돌면서 SHOW SLAVE STATUS로
# Replica에 직접 접속해 지연을 확인한다(PA-08).
resource "aws_vpc_security_group_ingress_rule" "db_replica_from_app" {
  security_group_id            = aws_security_group.db_replica.id
  description                  = "MariaDB monitoring query from app instance only"
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
  referenced_security_group_id = aws_security_group.app.id
}
