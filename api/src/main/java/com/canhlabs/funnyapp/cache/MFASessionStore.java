package com.canhlabs.funnyapp.cache;

import java.util.Optional;

public interface MFASessionStore {
    void storeSession(String sessionToken, String userId);

    Optional<String> getUserId(String sessionToken);

    void remove(String sessionToken);
}
