package com.canhlabs.funnyapp.share.totp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base32;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;


@Slf4j
public class TotpImpl implements Totp {

    private static final long TIME_STEP_SECONDS = 30;
    private static final int OTP_DIGITS = 6;
    private static final int ALLOWED_TIME_DRIFT_STEPS = 1;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final Base32 BASE32 = new Base32();

    /**
     * Generates a TOTP code based on the provided base32 secret.
     * It uses the current time step to generate the code.
     *
     * @param base32Secret The base32 encoded secret key used to generate the TOTP code.
     * @return The generated TOTP code as a string.
     */
    @Override
    public String generateTOTP(String base32Secret) {
        try {
            long currentStep = getTimeStep();
            return generateTotpForStep(base32Secret, currentStep);
        } catch (Exception e) {
            log.error("App Error: generateTOTP", e);
            throw new RuntimeException("Failed to generate TOTP", e);
        }
    }

    /**
     * Verifies the provided TOTP code against the expected code generated from the base32 secret.
     * It allows for a small time drift to account for clock differences.
     *
     * @param inputCode   The TOTP code to verify.
     * @param base32Secret The base32 encoded secret key used to generate the TOTP code.
     * @return True if the input code matches the expected code, false otherwise.
     */
    @Override
    public boolean verify(String inputCode, String base32Secret) {
        long currentStep = getTimeStep();

        try {
            for (int i = -ALLOWED_TIME_DRIFT_STEPS; i <= ALLOWED_TIME_DRIFT_STEPS; i++) {
                String expectedCode = generateTotpForStep(base32Secret, currentStep + i);
                if (expectedCode.equals(inputCode)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("App Error: verify", e);
        }

        return false;
    }

    /**
     * Gets the current time step based on the current epoch second divided by the time step duration.
     *
     * @return The current time step.
     */
    private static long getTimeStep() {
        return Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
    }

    /**
     * Generates a TOTP code for a specific time step using the provided base32 secret.
     *
     * @param base32Secret The base32 encoded secret key used to generate the TOTP code.
     * @param timeStep     The time step for which to generate the TOTP code.
     * @return The generated TOTP code as a string.
     * @throws Exception If an error occurs during HMAC generation.
     */
    private static String generateTotpForStep(String base32Secret, long timeStep) throws Exception {
        byte[] key = BASE32.decode(base32Secret);
        byte[] hmac = hmacSha1(key, timeStep);
        int otp = truncate(hmac);
        return  String.format("%0" + OTP_DIGITS + "d", otp);
    }

    /**
     * Generates an HMAC-SHA1 hash for the given key and time step.
     *
     * @param key       The secret key as a byte array.
     * @param timeStep The time step as a long value.
     * @return The HMAC-SHA1 hash as a byte array.
     * @throws Exception If an error occurs during HMAC generation.
     */
    private static byte[] hmacSha1(byte[] key, long timeStep) throws Exception {
        byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        return mac.doFinal(data);
    }

    /**
     * Truncate the HMAC to get a 6-digit OTP.
     *
     * @param hmac The HMAC byte array.
     * @return The truncated OTP as an integer.
     */
    private static int truncate(byte[] hmac) {
        int offset = hmac[hmac.length - 1] & 0x0F;
        int binary =
                ((hmac[offset] & 0x7f) << 24) |
                        ((hmac[offset + 1] & 0xff) << 16) |
                        ((hmac[offset + 2] & 0xff) << 8) |
                        (hmac[offset + 3] & 0xff);

        return binary % (int) Math.pow(10, OTP_DIGITS); // Đã bao toàn bộ biểu thức
    }

}