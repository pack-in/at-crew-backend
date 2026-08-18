package com.atcrew.common.mail;

public interface MailSender {

    void send(String to, String subject, String html);
}
