# Terraform — at-crew 인프라 보안 강화 (PA-03~05, PA-10)

`docs/design/infra-security-hardening-design.md`, `plans/260901-infra-upgrade/`(개인 문서)
참고. 기존 기본 VPC(`vpc-9f11ccf4`, laiteu와 공유)는 건드리지 않고 완전히 분리된 신규 VPC를
만든다.

## 포함된 것

- VPC(`10.20.0.0/16`, 단일 AZ) + Public/Private Subnet + IGW + 라우팅 테이블
- 자체 NAT 인스턴스(`t4g.nano`, 관리형 NAT Gateway 대신 — `docs/NEXT_STEPS.md` 기존 방침)
- 보안그룹 3개(NAT/앱/DB Replica) + Public·Private NACL
- Route53 Private Hosted Zone(**기본 비활성화** — 아래 참고)

## 포함되지 않은 것 (권한 부족으로 적용 불가)

2026-09-01 기준 `at-crew-be` IAM 사용자(`arn:aws:iam::820010786587:user/at-crew-be`) 권한을
`--dry-run`/실제 조회로 확인한 결과:

| 필요한 권한 | 상태 | 막힌 작업 |
|---|---|---|
| `iam:CreateRole`, `iam:PassRole` | 없음(AccessDenied) | SSM 인스턴스 프로파일 생성 — PA-02/PA-06 배포까지는 못 감 |
| `route53:*` | 없음(`ListHostedZones`부터 AccessDenied) | `route53.tf`의 리소스는 `route53_enabled=false`로 기본 비활성 |
| `ssm:GetParameter` | 없음 | 최신 AMI 조회를 SSM 퍼블릭 파라미터 대신 `aws_ami` data source(EC2 DescribeImages)로 대체 |
| `ec2:CreateVpc/Subnet/SecurityGroup/RunInstances` 등 | **있음**(dry-run 통과, 실제 인스턴스 3대 조회 확인) | 이 디렉토리의 리소스는 적용 가능 |

IAM·Route53 권한은 `plans/260901-infra-upgrade/PLAN-HUMAN.md` PH-01에서 root(sehandev)에게
요청한다.

## 사용법

```bash
cd deploy/terraform
terraform init
terraform plan          # route53_enabled=false 상태 — IAM/Route53 권한 없이도 plan/apply 가능
terraform apply
```

IAM 권한을 확보한 뒤 Route53까지 적용하려면:

```bash
terraform apply -var="route53_enabled=true"
```

## 주의

- `.terraform/`, `*.tfstate*`, `*.tfvars`는 `.gitignore`에 등록돼 있다 — 상태 파일에 실제 VPC/인스턴스 ID가 담기므로 커밋하지 않는다.
- 이 Terraform은 **신규 격리된 리소스만** 만든다. 기존 EC2#1(`at-crew-app-server`)·EC2#2(`at-crew-search-server`)·laiteu 서버는 이 코드가 손대지 않는다. 실제 앱 서버를 이 VPC로 옮기는 건 `PLAN-HUMAN.md` PH-07(blue-green 전환)에서 별도로 진행한다.
