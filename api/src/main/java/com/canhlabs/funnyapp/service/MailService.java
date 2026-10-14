package com.canhlabs.funnyapp.service;


import org.springframework.scheduling.annotation.Async;

import java.util.Locale;

public interface MailService {
    void sendSimpleMail(String to, String subject, String content);

    /**
     * Sends the magic-link email. Runs asynchronously, so the caller resolves the recipient's language
     * up front and passes it in; the implementation never reads the request locale (ADR-0020 D6).
     */
    @Async
    void sendInvitation(String to, String username, String verifyUrl, Locale locale);
}
