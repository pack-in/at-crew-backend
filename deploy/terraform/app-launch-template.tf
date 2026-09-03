# 앱 인스턴스 재생성용 시작 템플릿.
#
# 왜 aws_instance가 아니라 launch template인가:
#   현재 운영 중인 앱 인스턴스는 Terraform 밖에서 만들어졌다(blue-green 이전 때 수동 생성).
#   여기에 aws_instance를 선언하면 기존 인스턴스를 채택하는 게 아니라 **새 인스턴스를 하나 더**
#   만들려 든다. import로 채택할 수도 있으나 상태 불일치 위험이 있어(설계 문서 D5가 기존 리소스를
#   import하지 않기로 한 것과 같은 이유) 재생성 "설계도"만 코드로 고정한다.
#   장애 시 이 템플릿으로 인스턴스를 띄우면 아래 user_data가 자동으로 실행된다.
#
# 2026-09-03 RTO 훈련(docs/operations/baseline/2026-09-03-rto-drill.md)에서 이 파일이 없어서
# 겪은 일: deploy/README.md의 "최초 1회 설정"대로 인스턴스를 만들면 **SSM 에이전트가 설치되지
# 않아 접속할 수단이 하나도 없다.** SSH는 이슈 #122에서 없앴고 AL2023 minimal AMI에는 에이전트가
# 들어 있지 않기 때문이다. 21분을 헤매다 원인을 찾았다.

data "aws_ami" "app_al2023_arm64" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-minimal-*-kernel-*-arm64"]
  }

  filter {
    name   = "state"
    values = ["available"]
  }
}

resource "aws_launch_template" "app" {
  name          = "at-crew-app"
  description   = "앱 서버 재생성용. 장애 시 이 템플릿으로 인스턴스를 띄운다"
  image_id      = data.aws_ami.app_al2023_arm64.id
  instance_type = var.app_instance_type

  iam_instance_profile {
    # SSM 접속에 반드시 필요하다. 이게 없으면 에이전트가 떠도 등록되지 않는다.
    name = aws_iam_instance_profile.ssm.name
  }

  network_interfaces {
    subnet_id = aws_subnet.private.id
    # Private Subnet이므로 퍼블릭 IP를 주지 않는다. 인바운드는 Cloudflare Tunnel만 쓴다(D4).
    associate_public_ip_address = false
    security_groups             = [aws_security_group.app.id]
  }

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size           = var.app_root_volume_size
      volume_type           = "gp3"
      delete_on_termination = true
      encrypted             = true
    }
  }

  # AMI가 바뀌면 새 인스턴스에만 적용된다(기존 인스턴스는 건드리지 않는다).
  # NAT와 달리 lifecycle ignore_changes를 두지 않는 이유 — 이 템플릿은 "다음에 만들 때 쓸 설계도"라
  # 최신 AMI를 반영하는 편이 맞다.

  user_data = base64encode(<<-EOT
    #!/bin/bash
    # 앱 서버 최초 프로비저닝. deploy/README.md "최초 1회 설정"과 같은 내용이되,
    # 사람이 빠뜨리기 쉬운 SSM 에이전트를 맨 앞에 둔다.
    set -x

    # ── 1. SSM 에이전트 (가장 먼저) ─────────────────────────────
    # AL2023 minimal AMI에는 들어 있지 않다. 이게 없으면 이 인스턴스에 접속할 방법이 없다
    # (이슈 #122로 SSH 인바운드를 제거했다). 반드시 첫 단계여야 한다.
    dnf install -y amazon-ssm-agent \
      || dnf install -y https://s3.${var.aws_region}.amazonaws.com/amazon-ssm-${var.aws_region}/latest/linux_arm64/amazon-ssm-agent.rpm
    systemctl enable --now amazon-ssm-agent

    # ── 2. 컨테이너 런타임 ──────────────────────────────────────
    dnf install -y docker
    systemctl enable --now docker
    usermod -aG docker ec2-user
    curl -sL "https://github.com/docker/compose/releases/download/${var.docker_compose_version}/docker-compose-linux-aarch64" \
      -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose

    # ── 3. backup.sh가 R2 업로드에 쓴다 ─────────────────────────
    dnf install -y awscli-2 || dnf install -y aws-cli

    touch /var/log/at-crew-provisioned
  EOT
  )

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name = "at-crew-app"
    }
  }

  tags = {
    Name = "at-crew-app-launch-template"
  }
}
