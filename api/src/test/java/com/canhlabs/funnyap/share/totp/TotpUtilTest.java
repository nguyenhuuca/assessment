package com.canhlabs.funnyap.share.totp;

import com.canhlabs.funnyapp.share.totp.TotpUtil;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpUtilTest {

    // Sinh secret base32 giả lập như Google Authenticator (160-bit key)
    private String generateSecret() {
        byte[] buffer = new byte[20];
        new SecureRandom().nextBytes(buffer);
        return new Base32().encodeToString(buffer).replace("=", "").toUpperCase(Locale.ROOT);
    }

    @Test
    void testGenerateOtpFormat() {
        String secret = generateSecret();
        String otp = TotpUtil.generateTOTP(secret);

        assertNotNull(otp);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}"));
    }

    @Test
    void testOtpIsValidImmediately() {
        String secret = "CKWBTPMD3HIA2HGZCIQVL2ATN4CUJGIN";
        //String issuer = "funny-app";
//        String otpAuthUrl = String.format(
//                "otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=6&period=30",
//                issuer, "canh1", secret, issuer
//        );
        System.out.println(secret);
        String otp = TotpUtil.generateTOTP(secret);

        assertTrue(TotpUtil.verify(otp, secret));
    }

    @Test
    void testWrongOtpIsRejected() {
        String secret = generateSecret();
        String wrongOtp = "000000";

        assertFalse(TotpUtil.verify(wrongOtp, secret));
    }

    @Test
    void testOtpExpiredFails() throws InterruptedException {
        String secret = generateSecret();
        String otp = TotpUtil.generateTOTP(secret);

        // Sleep vượt qua window ±1 (~60s)
        Thread.sleep(35000);

        assertFalse(TotpUtil.verify(otp, secret));
    }

    @Test
    void testOtpStillValidWithinWindow() throws InterruptedException {
        String secret = generateSecret();
        String otp = TotpUtil.generateTOTP(secret);

        // Delay 20s => vẫn trong khoảng ±30s
        Thread.sleep(20000);

        assertTrue(TotpUtil.verify(otp, secret));
    }
}