package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.EmailCacheLimiter;
import com.canhlabs.funnyapp.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AppProperties appProperties;

    @Mock
    private AppProperties.EmailSetting emailSetting;

    @Mock
    EmailCacheLimiter emailCacheLimiter;

    @InjectMocks
    private MailServiceImpl mailService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
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
        // mock cấu hình
        when(appProperties.getEmailSetting()).thenReturn(emailSetting);
        when(emailSetting.getWhitelist()).thenReturn("ceo@abc.com,@trusted.com");
        when(emailSetting.getWhitelistAsList()).thenReturn(List.of("ceo@abc.com","@trusted.com"));


        mailService.sendInvitation("ceo@abc.com", "testuser", "http://verify.url");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_handlesExceptionGracefully() {
        // Simulate exception in mailSender.send
        doThrow(new RuntimeException("Mail error")).when(mailSender).send(any(MimeMessage.class));
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertThatCode(() ->
                ReflectionTestUtils.invokeMethod(mailService, "sendEmail", "to@abc.com", "subject", "<html>content</html>")
        ).doesNotThrowAnyException();
    }
    @Test
    void loadTemplate_readsTemplateFile() throws Exception {
        // Arrange
        when(appProperties.getInviteTemplate()).thenReturn("templates/email/invite-test.html");
        // Place a file named invite-test.html in src/test/resources with content: "Hello, {{username}}!"
        MailServiceImpl mailService = new MailServiceImpl(mailSender, appProperties , emailCacheLimiter);

        // Act
        mailService.loadTemplate();

        // Assert
        String template = (String) ReflectionTestUtils.getField(mailService, "htmlTemplate");
        assertThat(template).isEqualTo("Hello, {{username}}!");
    }
}