# 그린필드 VPC — 기존 기본 VPC(vpc-9f11ccf4, laiteu와 공유 중인 172.31.0.0/16)는 건드리지 않는다.
# 설계 문서 D5: 기존 리소스는 상태가 불명확해 import하지 않고, 완전히 분리된 신규 VPC를 만들어
# blue-green으로 전환한다.

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "at-crew-vpc"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "at-crew-igw"
  }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = true

  tags = {
    Name = "at-crew-public-2a"
  }
}

resource "aws_subnet" "private" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.private_subnet_cidr
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = false

  tags = {
    Name = "at-crew-private-2a"
  }
}

# Public Subnet: IGW로 직접 라우팅 (NAT 인스턴스만 여기 위치)
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "at-crew-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# Private Subnet: 인터넷행 트래픽은 전부 NAT 인스턴스 ENI를 거친다.
# NAT 인스턴스 없이는 아웃바운드도 전혀 안 된다는 걸 잊지 말 것
# (docs/NEXT_STEPS.md 2026-08-07 "프라이빗 서브넷은 아웃바운드도 막힌다" 항목과 동일 함정).
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block           = "0.0.0.0/0"
    network_interface_id = aws_instance.nat.primary_network_interface_id
  }

  tags = {
    Name = "at-crew-private-rt"
  }
}

resource "aws_route_table_association" "private" {
  subnet_id      = aws_subnet.private.id
  route_table_id = aws_route_table.private.id
}
