package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.EmailCacheLimiter;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.config.I18nConfig;
import com.canhlabs.funnyapp.service.MailService;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;
    private Map<String, String> htmlTemplates = Map.of();
    @Value("${spring.mail.username}")
    private String fromAddress;
    private final AppProperties appProperties;
    private final EmailCacheLimiter  emailLimiter;
    private final MessageSource messageSource;

    public MailServiceImpl(JavaMailSender mailSender, AppProperties appProperties, EmailCacheLimiter emailCacheLimiter,
                           MessageSource messageSource) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
        this.emailLimiter = emailCacheLimiter;
        this.messageSource = messageSource;
    }

    @PostConstruct
    public void loadTemplate() throws IOException {
        // Load one HTML template per supported language once at startup: <inviteTemplate>_<lang>.html
        Map<String, String> loaded = new HashMap<>();
        for (Locale locale : I18nConfig.SUPPORTED) {
            ClassPathResource resource = new ClassPathResource(
                    appProperties.getInviteTemplate() + "_" + locale.getLanguage() + ".html");
            try (InputStream is = resource.getInputStream()) {
                loaded.put(locale.getLanguage(), new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        htmlTemplates = Map.copyOf(loaded);
    }

    /** Template for the language, falling back to English. */
    String templateFor(Locale locale) {
        String template = locale == null ? null : htmlTemplates.get(locale.getLanguage());
        return template != null ? template : htmlTemplates.get(I18nConfig.EN.getLanguage());
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
    public void sendInvitation(String to, String username, String verifyUrl, Locale locale) {
        if(!shouldSend(to)) {
            log.warn("Email to {} is not sent due to preview settings", to);
            return;
        }
        Locale target = locale == null ? I18nConfig.EN : locale;
        String subject = messageSource.getMessage("mail.invite.subject", null, target);
        // Replace placeholders
        String content = templateFor(target)
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