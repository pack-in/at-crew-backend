# 관리형 NAT Gateway 대신 자체 NAT 인스턴스를 쓴다.
# 근거: docs/NEXT_STEPS.md "NAT Gateway 사용 금지(비용 폭탄 원인)", 설계 문서 D3.
# NAT Gateway 대비 월 $35~55 절감, 대신 이중화·처리량은 직접 감당한다(설계 문서 §5 리스크).

data "aws_ami" "al2023_arm64" {
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

resource "aws_instance" "nat" {
  ami                         = data.aws_ami.al2023_arm64.id
  instance_type               = var.nat_instance_type
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.nat.id]
  key_name                    = var.key_pair_name != "" ? var.key_pair_name : null
  associate_public_ip_address = true

  # NAT 인스턴스는 자기 앞으로 안 온 패킷도 전달해야 하므로 출발지/목적지 검사를 꺼야 한다.
  # 이걸 안 끄면 Private Subnet의 아웃바운드가 전부 막힌다.
  source_dest_check = false

  # QA(2026-09-02): data.aws_ami가 most_recent=true라, 나중에 AL2023이 새 AMI를 내면 다음 plan이
  # "교체" 를 제안한다 — 무심코 apply하면 Private Subnet 아웃바운드가 순단된다. AMI 교체는
  # 의도적으로 결정할 일이지 plan/apply 루틴에 묻어가면 안 된다.
  lifecycle {
    ignore_changes = [ami]
  }

  # QA(2026-09-01)로 발견해 고친 것 3가지:
  #   1) al2023-ami-minimal에는 iptables가 기본 포함이 아닐 수 있어 명시적으로 설치한다.
  #   2) FORWARD 체인 기본 정책이 원래 ACCEPT라 아래 ACCEPT 규칙들이 사실상 아무것도 막지 않았다
  #      (있으나 마나 한 규칙). 기본 정책을 DROP으로 바꾸고 나서야 규칙이 실제로 의미를 가진다.
  #   3) NAT의 특성상 UDP(DNS 응답 등)도 반드시 통과해야 하는데 원래 FORWARD 허용 규칙이 프로토콜을
  #      명시하지 않은 상태에서 정책이 ACCEPT였어서 우연히 통과됐을 뿐이다 — DROP으로 바꾸는 김에
  #      TCP/UDP 구분 없이 이 인스턴스가 다루는 규칙 전체를 명시적으로 남긴다.
  user_data = <<-EOF
    #!/bin/bash
    set -euo pipefail

    dnf install -y iptables

    echo 'net.ipv4.ip_forward = 1' > /etc/sysctl.d/99-nat.conf
    sysctl -p /etc/sysctl.d/99-nat.conf

    IFACE=$(ip -o -4 route show to default | awk '{print $5}')
    iptables -t nat -A POSTROUTING -o "$IFACE" -j MASQUERADE
    iptables -A FORWARD -i "$IFACE" -o "$IFACE" -m state --state RELATED,ESTABLISHED -j ACCEPT
    iptables -A FORWARD -s ${var.private_subnet_cidr} -j ACCEPT
    iptables -P FORWARD DROP

    iptables-save > /etc/nat-iptables.rules

    cat > /etc/systemd/system/nat-iptables.service <<'UNIT'
    [Unit]
    Description=NAT iptables 규칙 재부팅 후 복원
    After=network.target

    [Service]
    Type=oneshot
    ExecStart=/usr/sbin/iptables-restore /etc/nat-iptables.rules
    RemainAfterExit=yes

    [Install]
    WantedBy=multi-user.target
    UNIT

    systemctl daemon-reload
    systemctl enable nat-iptables.service
  EOF

  tags = {
    Name = "at-crew-nat-instance"
  }
}

# 탄력적 IP(2026-09-10 추가). NAT 인스턴스를 정지→시작하면 자동 할당 퍼블릭 IP가 바뀌고,
# 그게 곧 이 서비스 전체의 아웃바운드 IP다. 2026-09-03 볼륨 암호화 작업으로 NAT를 정지했을 때
# 실제로 자동 할당 퍼블릭 IP가 다른 값으로 바뀌었다.
#
# 지금은 아웃바운드 IP 허용목록을 요구하는 외부 연동이 없어 깨진 곳이 없었지만, 생기고 나서
# 바뀌면 우리가 먼저 알 수 없다 — 외부 서비스가 우리 요청을 거절하기 시작할 때(결제 웹훅·메일
# 발송이 조용히 실패하는 식) 알게 되고, 헬스체크에는 잡히지 않는다.
#
# 비용은 늘지 않는다. 2024-02부터 AWS는 "사용 중인" 모든 퍼블릭 IPv4에 시간당 $0.005를 매기고
# 자동 할당과 탄력적 IP의 요율이 같다(2026-08 청구서: APN2-PublicIPv4:InUseAddress $6.68 /
# 1,336.7시간). 추가 과금은 할당해 놓고 인스턴스에 붙이지 않은 유휴 상태(IdleAddress)뿐이다.
#
# ⚠️ 이 인스턴스를 종료할 때 탄력적 IP 해제(release)를 잊으면 유휴 상태로 계속 과금된다.
resource "aws_eip" "nat" {
  instance = aws_instance.nat.id
  domain   = "vpc"

  tags = {
    Name = "at-crew-nat-eip"
  }
}
