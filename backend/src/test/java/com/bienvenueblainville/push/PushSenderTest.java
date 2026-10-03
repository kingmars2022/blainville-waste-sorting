package com.bienvenueblainville.push;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * What the sender does with each answer a push service can give.
 *
 * <p>The distinction that matters is between "this device is gone" and "try
 * later". Deleting a subscription on a 503 loses a resident's notifications
 * permanently because a push service had a bad minute; keeping one on a 410
 * means paying for a guaranteed failure on every notice from now on.
 */
class PushSenderTest {
    private static final PushSubscription SUBSCRIPTION = new PushSubscription(
            7L, 3L, "https://push.example/wpush/v2/abc",
            "BAIX5hfwtkQ5KCePlpmeaaI6TywVK99tbN9m5bgCgtTtGUp968uXcS0t2jyoWqh2Wlb0X8dYWZZS8ol8ZTBuV5Q",
            "RERERERERERERERERERERA", "fr");

    @Mock
    private HttpClient http;
    @Mock
    @SuppressWarnings("rawtypes")
    private HttpResponse response;

    private PushSender sender;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        VapidAuthentication vapid = new VapidAuthentication(
                VapidKeys.of("BJX1f4oKaluqD9KfY868_KZyA0nNqCFao5xWFDuudr-N5QKNf61tFaOoHIRLI3uKRDTeAoHVfmhBhpW1hNadOb4",
                        "bfahS-4sCxZX09GPu1C-8oa586OYfKeI18tA3MbREPs"),
                "mailto:info@blainville.ca",
                Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC));
        sender = new PushSender(http, vapid);
    }

    @SuppressWarnings("unchecked")
    private PushSender.Result sendWithStatus(int status) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(http.send(any(), any())).thenReturn(response);
        return sender.send(SUBSCRIPTION, "{\"title\":\"x\"}");
    }

    @Test
    void acceptedIsSent() throws Exception {
        assertThat(sendWithStatus(201)).isEqualTo(PushSender.Result.SENT);
    }

    @Test
    void goneAndNotFoundMeanTheDeviceWillNeverAnswerAgain() throws Exception {
        assertThat(sendWithStatus(410)).isEqualTo(PushSender.Result.GONE);
        assertThat(sendWithStatus(404)).isEqualTo(PushSender.Result.GONE);
    }

    @Test
    void aBusyOrBrokenServiceIsNotAReasonToForgetTheDevice() throws Exception {
        assertThat(sendWithStatus(429)).isEqualTo(PushSender.Result.RETRYABLE);
        assertThat(sendWithStatus(503)).isEqualTo(PushSender.Result.RETRYABLE);
    }

    @Test
    void aRejectedRequestIsOurFaultAndSaysSo() throws Exception {
        assertThat(sendWithStatus(400)).isEqualTo(PushSender.Result.REJECTED);
        assertThat(sendWithStatus(401)).isEqualTo(PushSender.Result.REJECTED);
    }

    @Test
    void anUnreachableServiceIsRetryableRatherThanFatal() throws Exception {
        when(http.send(any(), any())).thenThrow(new IOException("connection reset"));

        assertThat(sender.send(SUBSCRIPTION, "{}")).isEqualTo(PushSender.Result.RETRYABLE);
    }

    @Test
    void storedKeysThatCannotBeUsedDropTheSubscriptionRatherThanFailingForever() {
        PushSubscription broken = new PushSubscription(
                8L, 3L, "https://push.example/wpush/v2/x", "bm90LWEta2V5", "RERERERERERERERERERERA", "fr");

        assertThat(sender.send(broken, "{}")).isEqualTo(PushSender.Result.GONE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsTheHeadersAPushServiceRequires() throws Exception {
        when(response.statusCode()).thenReturn(201);
        when(http.send(any(), any())).thenReturn(response);

        sender.send(SUBSCRIPTION, "{\"title\":\"x\"}");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(http).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("Content-Encoding")).contains("aes128gcm");
        // RFC 8030 requires TTL; a push service rejects the request without it.
        assertThat(request.headers().firstValue("TTL")).isPresent();
        assertThat(request.headers().firstValue("Authorization").orElseThrow()).startsWith("vapid t=");
    }
}
