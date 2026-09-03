# 확장 청사진 — 앱 2대 + RDS Multi-AZ.
#
# **기본값은 꺼져 있다(var.ha_enabled = false).** 지금은 만들지 않는다.
# 사용자가 늘어 근거가 생겼을 때 변수 하나만 바꿔 켠다.
#
# 왜 지금 켜지 않는가 (07-ha/ADR-01, 2026-09-03 측정 기준):
#   - 실사용자 0명, 한계 처리량 약 15 RPS. 자동 페일오버가 보호할 대상이 아직 없다
#   - RTO 실측 약 5분(docs/operations/baseline/2026-09-03-rto-drill.md). 현 규모에선 충분하다
#   - 켜면 월 약 $70이 추가된다 — 현재 at-crew 총액의 1.75배다
#
# 왜 그래도 코드로 두는가:
#   - 필요해진 시점에 설계부터 시작하면 늦다. 그때는 이미 사용자가 있다
#   - `terraform plan -var ha_enabled=true`로 **무엇이 얼마나 생기는지 지금 확인할 수 있다**
#   - 두 번째 AZ 서브넷은 비용이 0이라 미리 만들어 둔다(아래 참고)
#
# 전환 트리거는 07-ha/ADR-01-multi-az.md §7에 있다.

# ── 두 번째 AZ 서브넷 — ha_enabled와 무관하게 항상 만든다 ────────────────────
# 서브넷 자체는 무료다. RDS Multi-AZ가 서로 다른 AZ의 서브넷 2개를 요구하므로
# 미리 만들어 두면 전환 시점에 이것부터 만들 필요가 없다.
# 인스턴스를 넣지 않는 한 이 서브넷은 아무 비용도 만들지 않는다.
resource "aws_subnet" "private_secondary" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.private_subnet_secondary_cidr
  availability_zone       = var.availability_zone_secondary
  map_public_ip_on_launch = false

  tags = {
    Name = "at-crew-private-${var.availability_zone_secondary}"
  }
}

resource "aws_route_table_association" "private_secondary" {
  subnet_id      = aws_subnet.private_secondary.id
  route_table_id = aws_route_table.private.id
}

# ── 아래부터는 ha_enabled = true 일 때만 생긴다 ──────────────────────────────

# 두 번째 앱 인스턴스. Cloudflare Tunnel은 같은 터널에 여러 cloudflared(replica)를
# 붙일 수 있어(최대 25), 한 호스트가 죽어도 나머지로 트래픽이 간다.
# **ALB 없이 앱 계층 자동 페일오버가 된다** — 월 $20~38을 아낀다.
# 다만 replica는 트래픽 분산 알고리즘을 제공하지 않고 지리적으로 가까운 쪽으로 보낼 뿐이다.
# 능동적 헬스체크 기반 분산이 필요해지면 그때 Cloudflare Load Balancer를 검토한다.
resource "aws_instance" "app_secondary" {
  count = var.ha_enabled ? 1 : 0

  ami                    = data.aws_ami.app_al2023_arm64.id
  instance_type          = var.app_instance_type
  subnet_id              = aws_subnet.private_secondary.id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.ssm.name

  # 시작 템플릿과 같은 user_data를 쓴다 — SSM 에이전트가 맨 먼저 설치된다.
  # 템플릿 쪽은 base64로 저장돼 있으므로 user_data가 아니라 user_data_base64로 넘긴다
  # (user_data에 넣으면 Terraform이 한 번 더 인코딩해 스크립트가 깨진다).
  user_data_base64 = aws_launch_template.app.user_data

  root_block_device {
    volume_size           = var.app_root_volume_size
    volume_type           = "gp3"
    delete_on_termination = true
    encrypted             = true
  }

  lifecycle {
    ignore_changes = [ami]
  }

  tags = {
    Name = "at-crew-app-secondary"
  }
}

# ── RDS Multi-AZ ────────────────────────────────────────────────────────────
# self-hosted Replica 대신 RDS를 쓰는 이유는 **자동 페일오버** 하나다.
# self-hosted는 사람이 승격 스크립트를 실행해야 한다(설계 문서 D7이 자동화를 일부러 뺐다 —
# 소규모 팀의 자체 페일오버는 복제 지연을 장애로 오인해 split-brain을 만든다).
# RDS Multi-AZ는 그 판단을 AWS가 대신하고 60~120초에 전환한다.
resource "aws_db_subnet_group" "main" {
  count = var.ha_enabled ? 1 : 0

  name       = "at-crew-db"
  subnet_ids = [aws_subnet.private.id, aws_subnet.private_secondary.id]

  tags = {
    Name = "at-crew-db-subnet-group"
  }
}

resource "aws_security_group" "rds" {
  count = var.ha_enabled ? 1 : 0

  name        = "at-crew-rds-sg"
  description = "RDS. Inbound 3306 only from app SG"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "at-crew-rds-sg"
  }
}

# IP가 아니라 SG를 참조한다 — 앱 인스턴스가 늘거나 IP가 바뀌어도 규칙을 안 고쳐도 된다.
resource "aws_vpc_security_group_ingress_rule" "rds_from_app" {
  count = var.ha_enabled ? 1 : 0

  security_group_id            = aws_security_group.rds[0].id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 3306
  to_port                      = 3306
  ip_protocol                  = "tcp"
  description                  = "MariaDB from app instances only"
}

resource "aws_db_instance" "main" {
  count = var.ha_enabled ? 1 : 0

  identifier     = "at-crew-db"
  engine         = "mariadb"
  engine_version = var.rds_engine_version
  instance_class = var.rds_instance_class

  # ★ 이 한 줄이 자동 페일오버의 전부다. AWS가 다른 AZ에 standby를 동기 복제로 유지하고
  #   Primary 장애 시 60~120초에 전환한다. 엔드포인트는 하나이므로 앱은 아무것도 몰라도 된다.
  multi_az = true

  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "atcrew"
  username = "admin"
  # 비밀번호는 Terraform 상태에 평문으로 남지 않도록 AWS가 관리하게 맡긴다.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main[0].name
  vpc_security_group_ids = [aws_security_group.rds[0].id]
  publicly_accessible    = false

  # 백업은 R2 덤프(backup.sh)와 별개로 RDS 자체 스냅샷도 둔다.
  # RPO를 24시간에서 5분(PITR)으로 줄이는 것이 RDS 전환의 부수 효과다.
  backup_retention_period = var.rds_backup_retention_days
  backup_window           = "18:00-18:30" # UTC. 기존 backup.sh와 같은 시간대
  maintenance_window      = "sun:19:00-sun:19:30"

  # 실수로 지우는 것을 막는다. 지우려면 이 값을 false로 바꾼 apply가 한 번 더 필요하다.
  deletion_protection = true
  skip_final_snapshot = false
  final_snapshot_identifier = "at-crew-db-final"

  # 성능 문제를 나중에 추적할 수 있게 켠다. 무료 구간만 쓴다(7일 보관).
  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  tags = {
    Name = "at-crew-db"
  }
}
