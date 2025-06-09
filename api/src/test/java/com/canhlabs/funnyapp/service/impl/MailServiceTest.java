package com.canhlabs.funnyapp.service.impl;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private MailService mailService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Set required fields via reflection
        ReflectionTestUtils.setField(mailService, "fromAddress", "noreply@canh-labs.com");
        ReflectionTestUtils.setField(mailService, "htmlTemplate", "<html>{{username}} {{loginUrl}}</html>");
    }

    @Test
    void sendSimpleMail_sendsMail() {
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        mailService.sendSimpleMail("to@abc.com", "subject", "content");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendInvitation_sendsHtmlMail() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendInvitation("to@abc.com", "testuser", "http://verify.url");

        verify(mailSender).send(any(MimeMessage.class));
    }
}