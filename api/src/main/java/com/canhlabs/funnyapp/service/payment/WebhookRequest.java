package com.canhlabs.funnyapp.service.payment;

import java.util.Map;
import java.util.TreeMap;

/**
 * A provider webhook as received: headers (case-insensitive), the raw body (already size-capped) and the
 * remote address as the servlet container resolved it.
 */
public record WebhookRequest(Map<String, String> headers, byte[] body, String remoteAddr) {

    public WebhookRequest {
        Map<String, String> h = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            h.putAll(headers);
        }
        headers = h;
    }

    /** Header value, or null when absent. */
    public String header(String name) {
        return headers.get(name);
    }
}
