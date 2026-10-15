package com.canhlabs.funnyapp.client;

import java.util.Optional;

/** Reads where a short link redirects to, without following it. */
public interface RedirectProbe {

    /** Absolute target of a single 3xx response for {@code url}; empty on any other status, error or timeout. */
    Optional<String> location(String url);
}
