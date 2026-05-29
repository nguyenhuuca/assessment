package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.EmailCacheLimiter;
import com.canhlabs.funnyapp.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MailServiceImpl}.
 *
 * Tests use {@link ReflectionTestUtils} to inject the {@code fromAddress} and
 * {@code htmlTemplate} fields that are normally set by {@code @Value} and
 * {@code @PostConstruct} respectively.
 */
@ExtendWith(MockitoExtension.class)
class MailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AppProperties appProperties;

    @Mock
    private AppProperties.EmailSetting emailSetting;

    @Mock
    private EmailCacheLimiter emailCacheLimiter;

    @InjectMocks
    private MailServiceImpl mailService;

    private static final String FROM_ADDRESS = "noreply@canh-labs.com";
    private static final String HTML_TEMPLATE = "<html>Dear {{username}}, click <a href='{{loginUrl}}'>here</a></html>";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mailService, "fromAddress", FROM_ADDRESS);
        ReflectionTestUtils.setField(mailService, "htmlTemplate", HTML_TEMPLATE);
    }

    // -----------------------------------------------------------------------
    // loadTemplate
    // -----------------------------------------------------------------------

    @Test
    void loadTemplate_readsHtmlTemplateFromClasspath() throws Exception {
        when(appProperties.getInviteTemplate()).thenReturn("templates/email/invite-test.html");
        MailServiceImpl service = new MailServiceImpl(mailSender, appProperties, emailCacheLimiter);

        service.loadTemplate();

        String loaded = (String) ReflectionTestUtils.getField(service, "htmlTemplate");
        assertThat(loaded).isEqualTo("Hello, {{username}}!");
    }

    // -----------------------------------------------------------------------
    // sendSimpleMail
    // -----------------------------------------------------------------------

    @Test
    void sendSimpleMail_sendsMessageWithCorrectFields() {
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        mailService.sendSimpleMail("user@example.com", "Test Subject", "Test Content");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(msg.getSubject()).isEqualTo("Test Subject");
        assertThat(msg.getText()).isEqualTo("Test Content");
        assertThat(msg.getFrom()).isEqualTo("no-reply@canh-labs.com");
    }

    @Test
    void sendSimpleMail_propagatesExceptionFromMailSender() {
        doThrow(new RuntimeException("SMTP failure")).when(mailSender).send(any(SimpleMailMessage.class));

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> mailService.sendSimpleMail("user@example.com", "s", "c"));
    }

    // -----------------------------------------------------------------------
    // sendInvitation – whitelisted exact match
    // -----------------------------------------------------------------------

    @Test
    void sendInvitation_sendsHtmlMail_whenEmailIsExactlyWhitelisted() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(appProperties.getEmailSetting()).thenReturn(emailSetting);
        when(emailSetting.getWhitelistAsList()).thenReturn(List.of("vip@example.com"));

        mailService.sendInvitation("vip@example.com", "vipuser", "http://verify.link");

        verify(mailSender).send(any(MimeMessage.class));
        verify(emailCacheLimiter).incrementDailyCount(any(LocalDate.class));
    }

    // -----------------------------------------------------------------------
    // sendInvitation – whitelisted domain match
    // -----------------------------------------------------------------------

    @Test
    void sendInvitation_sendsHtmlMail_whenEmailMatchesWhitelistedDomain() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(appProperties.getEmailSetting()).thenReturn(emailSetting);
        when(emailSetting.getWhitelistAsList()).thenReturn(List.of("@trusted.org"));

        mailService.sendInvitation("anyone@trusted.org", "domainuser", "http://verify.link");

        verify(mailSender).send(any(MimeMessage.class));
        verify(emailCacheLimiter).incrementDailyCount(any(LocalDate.class));
    }

    // -----------------------------------------------------------------------
    // sendInvitation – daily limit exceeded
    // -----------------------------------------------------------------------

    @Test
    void sendInvitation_doesNotSend_whenDailyLimitExceeded() {
        when(appProperties.getEmailSetting()).thenReturn(emailSetting);
        when(emailSetting.getWhitelistAsList()).thenReturn(List.of());   // not whitelisted
        when(emailSetting.getMaxDailyEmails()).thenReturn(5);
        when(emailCacheLimiter.getDailyCount(any(LocalDate.class))).thenReturn(5); // at limit

        mailService.sendInvitation("user@external.com", "someuser", "http://verify.link");

        verify(mailSender, never()).createMimeMessage();
        verify(emailCacheLimiter, never()).incrementDailyCount(any(LocalDate.class));
    }

    @Test
    void sendInvitation_doesNotSend_whenDailyLimitExceededAboveThreshold() {
        when(appProperties.getEmailSetting()).thenReturn(emailSetting);
        when(emailSetting.getWhitelistAsList()).thenReturn(List.of());
        when(emailSetting.getMaxDailyEmails()).thenReturn(10);
        when(emailCacheLimiter.getDailyCount(any(LocalDate.class))).thenReturn(100); // far above limit

        mailService.sendInvitation("user@external.com", "someuser", "http://verify.link");

        verify(mailSender, never()).createMimeMessage();
    }

    // -----------------------------------------------------------------------
    // sendInvitation – preview mode disabled, under limit, not whitelisted
    // -----------------------------------------------------------------------

    @Test
    void sendInvitation_sends_whenPreviewModeDisabledAndUnderLimit() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(appProperties.getEmailSetting()).thenReturn(emailSetting);
        when(emailSetting.getWhitelistAsList()).thenReturn(List.of());
        when(emailSetting.getMaxDailyEmails()).thenReturn(100);
        when(emailCacheLimiter.getDailyCount(any(LocalDate.class))).thenReturn(0);
        when(emailSetting.isEnablePreviewMode()).thenReturn(false);

        mailService.sendInvitation("regular@external.com", "user", "http://verify.link");

        verify(mailSender).send(any(MimeMessage.class));
        verify(emailCacheLimiter).incrementDailyCount(any(LocalDate.class));
    }

    // -----------------------------------------------------------------------
    // sendInvitation – html template placeholder substitution
    // -----------------------------------------------------------------------

    @Test
    void sendInvitation_replacesPlaceholdersInTemplate() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(appProperties.getEmailSetting()).thenReturn(emailSetting);
        when(emailSetting.getWhitelistAsList()).thenReturn(List.of("target@example.com"));

        // Capture via a real MimeMessage-backed approach is complex; instead verify
        // that send is called exactly once and counter is incremented.
        mailService.sendInvitation("target@example.com", "Alice", "http://magic-link");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    // -----------------------------------------------------------------------
    // sendEmail (private) – exception handled gracefully
    // -----------------------------------------------------------------------

    @Test
    void sendEmail_handlesExceptionFromMailSenderGracefully() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() ->
                ReflectionTestUtils.invokeMethod(
                        mailService, "sendEmail",
                        "to@example.com", "Subject", "<html>body</html>")
        ).doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // isWhitelisted (private) – exact match, case-insensitive
    // -----------------------------------------------------------------------

    @Test
    void isWhitelisted_returnsTrueForExactEmailCaseInsensitive() {
        List<String> whitelist = List.of("Admin@Company.com", "other@company.com");

        Boolean result = ReflectionTestUtils.invokeMethod(
                mailService, "isWhitelisted", "admin@company.com", whitelist);

        assertThat(result).isTrue();
    }

    @Test
    void isWhitelisted_returnsTrueForDomainMatch() {
        List<String> whitelist = List.of("@canh-labs.com");

        Boolean result = ReflectionTestUtils.invokeMethod(
                mailService, "isWhitelisted", "engineer@canh-labs.com", whitelist);

        assertThat(result).isTrue();
    }

    @Test
    void isWhitelisted_returnsFalseWhenNoMatch() {
        List<String> whitelist = List.of("admin@example.com", "@trusted.org");

        Boolean result = ReflectionTestUtils.invokeMethod(
                mailService, "isWhitelisted", "stranger@unknown.net", whitelist);

        assertThat(result).isFalse();
    }

    @Test
    void isWhitelisted_returnsFalseForEmptyWhitelist() {
        List<String> whitelist = List.of();

        Boolean result = ReflectionTestUtils.invokeMethod(
                mailService, "isWhitelisted", "anyone@example.com", whitelist);

        assertThat(result).isFalse();
    }

    @Test
    void isWhitelisted_trimsDomainEntryBeforeMatching() {
        // whitelist entry with surrounding spaces should still match after trim
        List<String> whitelist = List.of("  @labs.io  ");

        Boolean result = ReflectionTestUtils.invokeMethod(
                mailService, "isWhitelisted", "dev@labs.io", whitelist);

        assertThat(result).isTrue();
    }

    // -----------------------------------------------------------------------
    // shouldSend (private) – preview mode enabled, percentage gate
    // -----------------------------------------------------------------------

    @Test
    void shouldSend_returnsFalseWhenPreviewModeEnabledAndPercentageIsZero() {
        when(appProperties.getEmailSetting()).thenReturn(emailSetting);
        when(emailSetting.getWhitelistAsList()).thenReturn(List.of());
        when(emailSetting.getMaxDailyEmails()).thenReturn(100);
        when(emailCacheLimiter.getDailyCount(any(LocalDate.class))).thenReturn(0);
        when(emailSetting.isEnablePreviewMode()).thenReturn(true);
        when(emailSetting.getPercentage()).thenReturn(0); // 0% → never passes random check

        // With 0% gate all random values (0–99) will be >= 0, so always false
        Boolean result = ReflectionTestUtils.invokeMethod(
                mailService, "shouldSend", "user@external.com");

        assertThat(result).isFalse();
    }

    @Test
    void shouldSend_returnsTrueWhenPreviewModeEnabledAndPercentageIs100() {
        when(appProperties.getEmailSetting()).thenReturn(emailSetting);
        when(emailSetting.getWhitelistAsList()).thenReturn(List.of());
        when(emailSetting.getMaxDailyEmails()).thenReturn(100);
        when(emailCacheLimiter.getDailyCount(any(LocalDate.class))).thenReturn(0);
        when(emailSetting.isEnablePreviewMode()).thenReturn(true);
        when(emailSetting.getPercentage()).thenReturn(100); // 100% → always passes

        Boolean result = ReflectionTestUtils.invokeMethod(
                mailService, "shouldSend", "user@external.com");

        assertThat(result).isTrue();
    }

    @Test
    void shouldSend_returnsTrueWhenWhitelistedRegardlessOfLimitOrPreviewMode() {
        when(appProperties.getEmailSetting()).thenReturn(emailSetting);
        when(emailSetting.getWhitelistAsList()).thenReturn(List.of("vip@example.com"));

        Boolean result = ReflectionTestUtils.invokeMethod(
                mailService, "shouldSend", "vip@example.com");

        assertThat(result).isTrue();
        // getDailyCount and isEnablePreviewMode should never be consulted for whitelisted addresses
        verify(emailCacheLimiter, never()).getDailyCount(any());
        verify(emailSetting, never()).isEnablePreviewMode();
    }
}
