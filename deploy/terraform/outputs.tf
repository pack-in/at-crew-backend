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
  value = aws_instance.nat.public_ip
}

output "app_security_group_id" {
  description = "PH-07 blue-green 전환 시 새 앱 인스턴스에 붙일 SG"
  value       = aws_security_group.app.id
}
