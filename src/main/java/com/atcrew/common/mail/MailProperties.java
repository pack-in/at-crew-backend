package com.atcrew.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 발송 설정 — Cloudflare Email Service(Email Sending REST API).
 *
 * <p>값이 비었거나 환경변수가 풀리지 않은 채(`${...}`)로 들어오면 기동을 멈춘다. 예전에는 키 없이도 앱이 떠서
 * 운영에서 비밀번호 재설정 메일이 조용히 전부 실패했다(2026-10-20 발견).
 *
 * @param fromAddress 발신 주소. Email Sending이 켜진 도메인(mail.at-crew.com)이어야 한다
 * @param fromName    발신자 표시 이름
 */
@ConfigurationProperties(prefix = "mail")
public record MailProperties(String fromAddress, String fromName, Cloudflare cloudflare) {

    public MailProperties {
        require("mail.from-address", fromAddress);
        if (cloudflare == null) {
            throw new IllegalStateException("mail.cloudflare 설정이 없다 — 메일을 보낼 수 없다.");
        }
        require("mail.cloudflare.account-id", cloudflare.accountId());
        require("mail.cloudflare.api-token", cloudflare.apiToken());
    }

    /** @param apiToken Email Sending 권한만 가진 API 토큰 */
    public record Cloudflare(String accountId, String apiToken) { }

    private static void require(String key, String value) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException(key + "가 비어 있다 — 메일을 보낼 수 없다.");
        }
    }
}
