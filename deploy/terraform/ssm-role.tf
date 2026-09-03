# PA-02 — SSM 접속용 IAM 역할. iam:PassRole 권한 확보(2026-09-02) 후 착수.
# 이게 붙으면 NAT 점프호스트로 SSH 여닫는 방식 대신 SSM Session Manager로 접속할 수 있다 —
# 인바운드 포트 자체가 필요 없고, 접속 이력이 CloudTrail에 남는다(deploy/README.md TODO 항목).
resource "aws_iam_role" "ssm" {
  name = "at-crew-ssm-instance-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.ssm.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ssm" {
  name = "at-crew-ssm-instance-profile"
  role = aws_iam_role.ssm.name
}

output "ssm_instance_profile_name" {
  value = aws_iam_instance_profile.ssm.name
}
