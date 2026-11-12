package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.config.AppProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Builds the SePay VietQR image URL: {@code {qrBaseUrl}?acc=&bank=&amount=&des=&template=compact}. */
public final class VietQrUrlBuilder {

    private VietQrUrlBuilder() {
    }

    public static String build(AppProperties.Sepay sepay, long amount, String code) {
        return sepay.getQrBaseUrl()
                + "?acc=" + enc(sepay.getBankAccount())
                + "&bank=" + enc(sepay.getBankCode())
                + "&amount=" + amount
                + "&des=" + enc(code)
                + "&template=compact";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
