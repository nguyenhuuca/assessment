package com.canhlabs.funnyapp.utils.totp;

public interface Totp {
    /**
     * Generates a TOTP code based on the provided secret key and current time.
     *
     * @param secret The secret key used to generate the TOTP code.
     * @return The generated TOTP code as a string.
     */
    String generateTOTP(String secret);

    /**
     * Validates a TOTP code against the provided secret key and current time.
     *
     * @param secret The secret key used to validate the TOTP code.
     * @param totp   The TOTP code to validate.
     * @return True if the TOTP code is valid, false otherwise.
     */
    boolean verify( String totp, String secret);
}
