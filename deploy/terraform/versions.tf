terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  # tfstate 원격 백엔드 — 2026-09-03. 로컬 1곳에만 있던 상태를 옮겼다(PLAN-HUMAN PH-05 QA 지적
  # 해소). AWS S3 대신 이미 DB 백업으로 쓰는 Cloudflare R2 버킷을 재사용한다(S3 호환 API) — 새 AWS
  # IAM 권한이나 새 Cloudflare 리소스가 필요 없다.
  #
  # profile = "r2-tfstate": 이 컴퓨터의 ~/.aws/credentials에 R2 자격증명을 별도 프로필로 등록해
  # 참조한다. AWS_ACCESS_KEY_ID 환경변수로 넘기면 아래 aws 프로바이더(실제 AWS 리소스용, at-crew-be
  # IAM 계정)가 같은 환경변수를 먼저 집어가 R2 키로 AWS를 인증하려다 깨진다 — 실제로 겪은 충돌이라
  # 두 자격증명을 프로필로 분리했다.
  #
  # 락 없음: Terraform 1.9.8이라 S3 네이티브 락(use_lockfile, 1.10+)을 못 쓰고, R2엔 DynamoDB가
  # 없다. 지금은 이 저장소에 Terraform을 쓰는 사람이 1명이라 당장 위험은 낮지만, apply 전에는 항상
  # `terraform plan`으로 먼저 diff를 확인할 것 — 동시 apply 충돌을 코드로 막아주지 않는다.
  backend "s3" {
    bucket  = "at-crew-storage-backups"
    key     = "terraform/at-crew.tfstate"
    region  = "auto"
    profile = "r2-tfstate"
    endpoints = {
      s3 = "https://8ffe00cd867bc560cfef7b6ab0711b14.r2.cloudflarestorage.com"
    }
    skip_credentials_validation = true
    skip_region_validation      = true
    skip_requesting_account_id  = true
    skip_s3_checksum            = true
  }
}

provider "aws" {
  region  = var.aws_region
  profile = "default"
}

# PA-11 — SSL 모드/DNS/WAF/Tunnel을 코드로 관리할 때 쓴다(docs/design/infra-security-hardening-design.md).
# 토큰 값은 terraform.tfvars(gitignore 처리됨, 커밋 안 됨)에서만 채운다.
provider "cloudflare" {
  api_token = var.cloudflare_api_token
}
