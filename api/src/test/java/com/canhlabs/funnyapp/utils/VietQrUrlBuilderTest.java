package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VietQrUrlBuilderTest {

    @Test
    void build_encodesEveryParameter() {
        AppProperties.Sepay sepay = new AppProperties.Sepay();
        sepay.setQrBaseUrl("https://qr.example/img");
        sepay.setBankAccount("0123 456");
        sepay.setBankCode("MB&Bank");

        String url = VietQrUrlBuilder.build(sepay, 50000, "FMAB12CD34");

        assertThat(url).isEqualTo(
                "https://qr.example/img?acc=0123+456&bank=MB%26Bank&amount=50000&des=FMAB12CD34&template=compact");
    }
}
