output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_id" {
  value = aws_subnet.public.id
}

output "private_subnet_id" {
  value = aws_subnet.private.id
}

output "nat_instance_id" {
  value = aws_instance.nat.id
}

output "nat_instance_public_ip" {
  description = "이 서비스 전체의 아웃바운드 IP. 탄력적 IP라 인스턴스를 정지·재생성해도 바뀌지 않는다"
  value       = aws_eip.nat.public_ip
}

output "app_security_group_id" {
  description = "PH-07 blue-green 전환 시 새 앱 인스턴스에 붙일 SG"
  value       = aws_security_group.app.id
}
