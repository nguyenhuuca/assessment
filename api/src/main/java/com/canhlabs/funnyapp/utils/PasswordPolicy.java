package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NIST SP 800-63B style password policy (ADR-0018): min 10 characters, max 72 UTF-8 bytes (BCrypt limit),
 * must not contain the email local part, must not be a known common password. No composition rules.
 * Violations raise a 400 {@link CustomException} whose message is a stable error code.
 */
@Slf4j
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;
    public static final int MIN_EMAIL_LOCAL_LENGTH = 3;

    public static final String TOO_SHORT = "PASSWORD_TOO_SHORT";
    public static final String TOO_LONG = "PASSWORD_TOO_LONG";
    public static final String CONTAINS_EMAIL = "PASSWORD_CONTAINS_EMAIL";
    public static final String TOO_COMMON = "PASSWORD_TOO_COMMON";

    private static final String COMMON_PASSWORDS_RESOURCE = "common-passwords.txt";
    private static final Set<String> COMMON_PASSWORDS = loadCommonPasswords();

    private PasswordPolicy() {
    }

    /**
     * @param password the candidate password
     * @param email    the account email (may be null)
     * @throws CustomException 400 with one of the policy error codes
     */
    public static void validate(String password, String email) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw violation(TOO_SHORT);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw violation(TOO_LONG);
        }
        String lower = password.toLowerCase(Locale.ROOT);
        String localPart = localPart(email);
        if (localPart.length() >= MIN_EMAIL_LOCAL_LENGTH && lower.contains(localPart)) {
            throw violation(CONTAINS_EMAIL);
        }
        if (COMMON_PASSWORDS.contains(lower)) {
            throw violation(TOO_COMMON);
        }
    }

    static int commonPasswordCount() {
        return COMMON_PASSWORDS.size();
    }

    private static String localPart(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        String local = at >= 0 ? email.substring(0, at) : email;
        return local.trim().toLowerCase(Locale.ROOT);
    }

    private static CustomException violation(String code) {
        return CustomException.builder()
                .status(HttpStatus.BAD_REQUEST)
                .subCode(400)
                .message(code)
                .build();
    }

    private static Set<String> loadCommonPasswords() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(COMMON_PASSWORDS_RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            Set<String> set = reader.lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty())
                    .map(l -> l.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            log.info("Loaded {} common passwords", set.size());
            return set;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + COMMON_PASSWORDS_RESOURCE, e);
        }
    }
}
