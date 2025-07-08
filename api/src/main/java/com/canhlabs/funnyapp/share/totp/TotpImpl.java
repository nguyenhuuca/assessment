package com.canhlabs.funnyapp.share.totp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base32;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;


@Slf4j
public class TotpImpl implements Totp {
    public TotpImpl() {}

    private static final long TIME_STEP = 30;
    private static final int DIGITS = 6;

    public String generateTOTP(String base32Secret) {
        try {
            long t = Instant.now().getEpochSecond() / TIME_STEP;
            byte[] key = decodeBase32(base32Secret);
            byte[] hmac = hmacSha1(key, t);
            int otp = truncate(hmac);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            log.error("App Error: generateTOTP", e);
            throw new RuntimeException("Failed to generate TOTP", e);
        }
    }

    public boolean verify(String code, String secret) {
        long time = Instant.now().getEpochSecond() / TIME_STEP;

        try {
            for (int i = -1; i <= 1; i++) {
                byte[] hmac = hmacSha1(decodeBase32(secret), time + i);
                int otp = truncate(hmac);
                if (code.equals(String.format("%06d", otp))) return true;
            }
        } catch (Exception e) {
           log.error("App Error: verify", e);
        }

        return false;
    }

    private static byte[] decodeBase32(String base32) {
        // (Google Authenticator use RFC4648)
        return new Base32().decode(base32);
    }

    private static byte[] hmacSha1(byte[] key, long t) throws Exception {
        byte[] data = ByteBuffer.allocate(8).putLong(t).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        return mac.doFinal(data);
    }

    private static int truncate(byte[] hmac) {
        int offset = hmac[hmac.length - 1] & 0x0F;
        int binary =
                ((hmac[offset] & 0x7f) << 24) |
                        ((hmac[offset + 1] & 0xff) << 16) |
                        ((hmac[offset + 2] & 0xff) << 8) |
                        (hmac[offset + 3] & 0xff);
        return binary % 1_000_000;
    }
}