package com.canhlabs.funnyapp.service;


import org.springframework.scheduling.annotation.Async;

public interface MailService {
    void sendSimpleMail(String to, String subject, String content);

    @Async
    void sendInvitation(String to, String username, String verifyUrl);
}
