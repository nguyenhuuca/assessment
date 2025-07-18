package com.canhlabs.funnyapp.share.totp;

import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpImplTest {

    // Sinh secret base32 giả lập như Google Authenticator (160-bit key)
    private String generateSecret() {
        byte[] buffer = new byte[20];
        new SecureRandom().nextBytes(buffer);
        return new Base32().encodeToString(buffer).replace("=", "").toUpperCase(Locale.ROOT);
    }

    @Test
    void testGenerateOtpFormat() {
        Totp totp = new TotpImpl();
        String secret = generateSecret();
        String otp = totp.generateTOTP(secret);

        assertNotNull(otp);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}"));
    }

    @Test
    void testOtpIsValidImmediately() {
        Totp totp = new TotpImpl();
        String secret = "CKWBTPMD3HIA2HGZCIQVL2ATN4CUJGIN";
        System.out.println(secret);
        String otp = totp.generateTOTP(secret);

        assertTrue(totp.verify(otp, secret));
    }

    @Test
    void testWrongOtpIsRejected() {
        Totp totp = new TotpImpl();
        String secret = generateSecret();
        String wrongOtp = "000000";

        assertFalse(totp.verify(wrongOtp, secret));
    }

    @Disabled
    @Test
    void testOtpExpiredFails() throws InterruptedException {
        Totp totp = new TotpImpl();
        String secret = generateSecret();
        String otp = totp.generateTOTP(secret);

        Thread.sleep(35000);

        assertFalse(totp.verify(otp, secret));
    }

    @Test
    void testOtpStillValidWithinWindow() throws InterruptedException {
        Totp totp = new TotpImpl();
        String secret = generateSecret();
        String otp = totp.generateTOTP(secret);

        Thread.sleep(1000);

        assertTrue(totp.verify(otp, secret));
    }
}