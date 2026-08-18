package com.atcrew.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mail.resend")
public record MailProperties(String apiKey, String fromAddress) { }
