variable "aws_region" {
  description = "리전 — 기존 EC2(#1 app, #2 search)와 동일하게 맞춘다"
  type        = string
  default     = "ap-northeast-2"
}

variable "availability_zone" {
  description = "단일 AZ로 시작한다 — 앱 인스턴스가 1대뿐이라 멀티 AZ 이중화는 과설계 (설계 문서 §4 Phase 3)"
  type        = string
  default     = "ap-northeast-2a"
}

variable "vpc_cidr" {
  description = "신규 VPC CIDR. 기존 기본 VPC(172.31.0.0/16, laiteu와 공유)와 겹치지 않게 별도 대역 사용"
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidr" {
  description = "Public Subnet — NAT 인스턴스만 배치"
  type        = string
  default     = "10.20.0.0/24"
}

variable "private_subnet_cidr" {
  description = "Private Subnet — 앱 서버·DB Replica 배치"
  type        = string
  default     = "10.20.1.0/24"
}

variable "nat_instance_type" {
  description = "NAT 인스턴스 — 관리형 NAT Gateway 대신 자체 구축(비용 절감, docs/NEXT_STEPS.md 기존 방침)"
  type        = string
  default     = "t4g.nano"
}

variable "key_pair_name" {
  description = "NAT 인스턴스 SSH 키페어 이름. 기존 EC2(#1,#2)와 동일한 at-crew-key 재사용. 현재는 SSM이 IAM 권한 부족으로 도입 전이라 임시로 필요(PLAN-HUMAN PH-01 완료 후 SSM 전용으로 전환하고 이 값 제거 검토) — SG에서 22번 인바운드는 기본 열지 않으므로 이 키만으로는 접속 불가, 필요 시 트러블슈팅용으로 한시적으로만 SG 규칙 추가할 것"
  type        = string
  default     = "at-crew-key"
}

variable "route53_enabled" {
  description = "Route53 Private Hosted Zone 생성 여부. 2026-09-02 route53:* 권한 확보를 실제 생성/삭제 테스트로 확인함(PH-01 일부 완료) — 다만 IAM 역할 관련 권한은 아직 막혀 있어 기본값은 그대로 false로 둔다. apply 시점에 이 부분만 별도로 true 전환 여부를 확인할 것"
  type        = bool
  default     = false
}

variable "cloudflare_api_token" {
  description = "Cloudflare API 토큰(PA-11) — Zone: SSL/DNS/WAF/Firewall Services Edit, Account: Cloudflare Tunnel Edit, at-crew.com 존에 한정. 실제 값은 terraform.tfvars(gitignore)에서만 채운다. 여기 기본값을 두지 않는다 — 값 없이 apply하면 즉시 에러로 멈추는 게, 빈 문자열로 조용히 인증 실패하는 것보다 낫다."
  type        = string
  sensitive   = true
}

variable "cloudflare_zone_id" {
  description = "at-crew.com Zone ID. Cloudflare 대시보드 Overview 우측 하단에서 확인. 기본값을 두지 않는다 — 빈 값으로 조용히 실패하는 것보다 apply 시점에 바로 에러로 멈추는 게 낫다(QA 2026-09-02)."
  type        = string
}

variable "cloudflare_account_id" {
  description = "Cloudflare Account ID. wrangler.toml의 CLOUDFLARE_ACCOUNT_ID와 같은 값. 기본값을 두지 않는다(위와 같은 이유)."
  type        = string
}

variable "waf_enabled" {
  description = "Cloudflare WAF 관리형 룰 적용 여부. 기본 false — apply 즉시 라이브 트래픽에 영향을 주는 유일한 리소스라 별도로 지켜볼 수 있을 때 true로 전환한다(PLAN-HUMAN PH-10, QA 2026-09-02 지적 반영)."
  type        = bool
  default     = false
}

variable "app_instance_type" {
  description = "앱 서버 인스턴스 타입. 2026-09-02 부하 측정 기준 t4g.medium에서 한계 처리량 약 15 RPS"
  type        = string
  default     = "t4g.medium"
}

variable "app_root_volume_size" {
  description = "앱 서버 루트 볼륨(GB). 도커 이미지·로그·DB 볼륨이 여기 들어간다"
  type        = number
  default     = 30
}

variable "docker_compose_version" {
  description = "docker-compose standalone 바이너리 버전(플러그인 아님 — deploy/README.md 참고)"
  type        = string
  default     = "v2.29.7"
}

# ── 확장 청사진(ha-blueprint.tf) ─────────────────────────────────────────────
variable "ha_enabled" {
  description = <<-EOT
    앱 2대 + RDS Multi-AZ를 실제로 만들지 여부. 기본은 false다.
    켜기 전에 07-ha/ADR-01-multi-az.md §7의 전환 트리거를 확인할 것.
    `terraform plan -var ha_enabled=true`로 무엇이 생기는지 먼저 본다.
  EOT
  type        = bool
  default     = false
}

variable "availability_zone_secondary" {
  description = "두 번째 AZ. 서울은 2a~2d 모두 t4g를 지원하므로 어느 것이든 무방하다(2026-09-03 확인)"
  type        = string
  default     = "ap-northeast-2c"
}

variable "private_subnet_secondary_cidr" {
  description = "두 번째 AZ의 Private Subnet. 서브넷 자체는 무료라 ha_enabled와 무관하게 만든다"
  type        = string
  default     = "10.20.2.0/24"
}

variable "rds_instance_class" {
  description = "RDS 인스턴스 클래스. Multi-AZ는 단일 대비 약 2배 요금이다"
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_engine_version" {
  description = "self-hosted와 같은 계열을 쓴다(현재 mariadb:11.4 컨테이너)"
  type        = string
  default     = "11.4"
}

variable "rds_allocated_storage" {
  description = "초기 스토리지(GB). max_allocated_storage까지 자동 확장된다"
  type        = number
  default     = 20
}

variable "rds_max_allocated_storage" {
  description = "자동 확장 상한(GB)"
  type        = number
  default     = 100
}

variable "rds_backup_retention_days" {
  description = "RDS 자동 백업 보관 일수. 1 이상이면 PITR이 켜져 RPO가 5분 수준이 된다"
  type        = number
  default     = 7
}
