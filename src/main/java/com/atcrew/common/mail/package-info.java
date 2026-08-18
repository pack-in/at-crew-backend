// 메일 발송 포트(MailSender) + Resend 어댑터. 도메인이 아닌 인프라이므로 common에 둔다
// (docs/design/auth-email-custom-redesign.md §7.3).
@org.springframework.modulith.NamedInterface("mail")
package com.atcrew.common.mail;
