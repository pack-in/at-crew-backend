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
}

provider "aws" {
  region = var.aws_region
}

# PA-11 — SSL 모드/DNS/WAF/Tunnel을 코드로 관리할 때 쓴다(docs/design/infra-security-hardening-design.md).
# 토큰 값은 terraform.tfvars(gitignore 처리됨, 커밋 안 됨)에서만 채운다.
provider "cloudflare" {
  api_token = var.cloudflare_api_token
}
