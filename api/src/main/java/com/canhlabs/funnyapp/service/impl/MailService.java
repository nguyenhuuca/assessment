package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.share.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private String htmlTemplate;
    @Value("${spring.mail.username}")
    private String fromAddress;
    private final AppProperties appProperties;


    public MailService(JavaMailSender mailSender, AppProperties appProperties) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void loadTemplate() throws IOException {
        // Load HTML template once at startup
        ClassPathResource resource = new ClassPathResource(appProperties.getInviteTemplate());
        try (InputStream is = resource.getInputStream()) {
            htmlTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public void sendSimpleMail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@canh-labs.com");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }

    @Async
    public void sendInvitation(String to, String username, String verifyUrl) {
        String subject = "Enable Your Account";
        // Replace placeholders
        String content = htmlTemplate
                .replace("{{username}}", username)
                .replace("{{loginUrl}}", verifyUrl);

        sendEmail(to, subject, content);
        log.info("Send email to {} success", to);
    }


    private void sendEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true); // true for HTML
            helper.setFrom(new InternetAddress(fromAddress, "No Reply(canh-labs.com)"));
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Send email err", e);
        }
    }
}