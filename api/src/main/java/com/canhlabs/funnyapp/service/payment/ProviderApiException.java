package com.canhlabs.funnyapp.service.payment;

/** A provider API could not be asked: {@code configured} is false when its credentials are missing. */
public class ProviderApiException extends RuntimeException {

    private final boolean configured;

    public ProviderApiException(String message, boolean configured) {
        super(message);
        this.configured = configured;
    }

    public boolean isConfigured() {
        return configured;
    }
}
