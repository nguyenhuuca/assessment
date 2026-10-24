package com.canhlabs.funnyapp.config;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.testing.http.MockHttpTransport;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigBeanDriveTimeoutsTest {

    @Test
    void withTimeouts_setsTimeoutsAndStillRunsCredentialsInitializer() throws Exception {
        AtomicInteger credentialCalls = new AtomicInteger();
        HttpRequestInitializer credentials = request -> credentialCalls.incrementAndGet();

        HttpRequest request = new MockHttpTransport().createRequestFactory(ConfigBean.withTimeouts(credentials))
                .buildGetRequest(new GenericUrl("https://www.googleapis.com/drive/v3/files"));

        assertThat(credentialCalls.get()).isEqualTo(1);
        assertThat(request.getConnectTimeout()).isEqualTo(10_000);
        assertThat(request.getReadTimeout()).isEqualTo(60_000);
    }
}
