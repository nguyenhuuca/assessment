package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.EmailCacheLimiter;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.service.MailService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;
    private String htmlTemplate;
    @Value("${spring.mail.username}")
    private String fromAddress;
    private final AppProperties appProperties;
    private final EmailCacheLimiter  emailLimiter;

    public MailServiceImpl(JavaMailSender mailSender, AppProperties appProperties, EmailCacheLimiter emailCacheLimiter) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
        this.emailLimiter = emailCacheLimiter;
    }

    @PostConstruct
    public void loadTemplate() throws IOException {
        // Load HTML template once at startup
        ClassPathResource resource = new ClassPathResource(appProperties.getInviteTemplate());
        try (InputStream is = resource.getInputStream()) {
            htmlTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void sendSimpleMail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@canh-labs.com");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }

    @Async
    @Override
    public void sendInvitation(String to, String username, String verifyUrl) {
        if(!shouldSend(to)) {
            log.warn("Email to {} is not sent due to preview settings", to);
            return;
        }
        String subject = "Enable Your Account";
        // Replace placeholders
        String content = htmlTemplate
                .replace("{{username}}", username)
                .replace("{{loginUrl}}", verifyUrl);

        sendEmail(to, subject, content);
        emailLimiter.incrementDailyCount(LocalDate.now());
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

    private boolean shouldSend(String email) {
        List<String> whitelist = appProperties.getEmailSetting().getWhitelistAsList();// đã parse từ CSV env
        int percentage = appProperties.getEmailSetting().getPercentage();
        int maxPerDay = appProperties.getEmailSetting().getMaxDailyEmails();

        if (isWhitelisted(email, whitelist)) return true;

        int count = emailLimiter.getDailyCount(LocalDate.now());
        if (count >= maxPerDay) {
            log.warn("Email limit exceeded, email limit reached {} for today", maxPerDay);
            return false;
        }

        // Randomly decide to send email based on percentage
        if(appProperties.getEmailSetting().isEnablePreviewMode()) {
            int rand = ThreadLocalRandom.current().nextInt(100);
            if (rand < percentage) {
                return true;
            } else {
                log.warn("Email {} is not sent, random percentage {} >= {}", email, rand, percentage);
                return false;
            }
        }
        return true;
    }

    private boolean isWhitelisted(String email, List<String> whitelist) {
        for (String entry : whitelist) {
            entry = entry.trim().toLowerCase();
            if (entry.startsWith("@")) {
                // domain match
                if (email.toLowerCase().endsWith(entry)) return true;
            } else {
                // exact match
                if (email.equalsIgnoreCase(entry)) return true;
            }
        }
        log.warn("Email {} is not whitelisted", email);
        return false;
    }
}