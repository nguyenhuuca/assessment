package com.canhlabs.funnyapp.cache;

import java.util.Optional;

public interface MFASessionStore {
    void storeSession(String sessionToken, String userId);

    Optional<String> getUserId(String sessionToken);

    /**
     * Records one wrong OTP for the session.
     *
     * @return the number of failures so far for this session token
     */
    int recordFailure(String sessionToken);

    /** Removes the session and its failure counter. */
    void remove(String sessionToken);
}
