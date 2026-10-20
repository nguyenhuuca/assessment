package com.canhlabs.funnyapp.utils;

import java.util.Optional;

/** Reads where a short link redirects to, without following it. */
public interface RedirectProbe {

    /**
     * Absolute target of a single 3xx response for {@code url}; empty when the server answered without a redirect.
     *
     * @throws Unavailable when no answer was obtained (busy, timeout, network error): a transient failure the
     *                     caller must not cache
     */
    Optional<String> location(String url);

    /** Transient probe failure; the message never contains the URL. */
    class Unavailable extends RuntimeException {
        public Unavailable(String reason) {
            super(reason, null, false, false);
        }
    }
}
