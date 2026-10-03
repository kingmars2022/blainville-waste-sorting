package com.bienvenueblainville.push;

import com.bienvenueblainville.notice.SpecialNotice;
import com.bienvenueblainville.notice.SpecialNoticeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The fan-out, and what it is not allowed to do.
 *
 * <p>Push is the second delivery of a notice the resident inbox already holds.
 * Every test here is really the same assertion from a different angle: no
 * failure in this path may reach the consumer that called it, because the
 * consumer's other job already succeeded.
 */
class PushNotifierTest {
    private static final SpecialNotice NOTICE = new SpecialNotice(
            12L, LocalDate.parse("2026-12-20"), LocalDate.parse("2026-12-27"),
            "Collecte deplacee", "Collection moved", "收集顺延",
            "Noel.", "Christmas.", "圣诞节。", null, true);

    @Mock
    private PushSubscriptionMapper subscriptions;
    @Mock
    private SpecialNoticeMapper notices;
    @Mock
    private PushSender sender;

    private PushNotifier notifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        notifier = new PushNotifier(subscriptions, notices, sender, new ObjectMapper(), true);
    }

    private static PushSubscription subscription(long id, String language) {
        return new PushSubscription(id, id, "https://push.example/" + id, "key", "auth", language);
    }

    @Test
    void sendsEachDeviceTheLanguageItAskedFor() {
        when(notices.findById(12L)).thenReturn(Optional.of(NOTICE));
        when(subscriptions.findForResidentsWithReminders())
                .thenReturn(List.of(subscription(1, "fr"), subscription(2, "en"), subscription(3, "zh")));
        when(sender.send(any(), any())).thenReturn(PushSender.Result.SENT);

        notifier.pushNotice(12L);

        ArgumentCaptor<String> payloads = ArgumentCaptor.forClass(String.class);
        verify(sender, org.mockito.Mockito.times(3)).send(any(), payloads.capture());
        assertThat(payloads.getAllValues().get(0)).contains("Collecte deplacee");
        assertThat(payloads.getAllValues().get(1)).contains("Collection moved");
        assertThat(payloads.getAllValues().get(2)).contains("收集顺延");
    }

    @Test
    void removesASubscriptionThePushServiceSaysIsGone() {
        when(notices.findById(12L)).thenReturn(Optional.of(NOTICE));
        when(subscriptions.findForResidentsWithReminders()).thenReturn(List.of(subscription(1, "fr")));
        when(sender.send(any(), any())).thenReturn(PushSender.Result.GONE);

        notifier.pushNotice(12L);

        verify(subscriptions).deleteByEndpoint("https://push.example/1");
    }

    @Test
    void keepsASubscriptionWhenTheServiceWasMerelyBusy() {
        // Deleting here would cost a resident every future notification
        // because a push service had a bad minute.
        when(notices.findById(12L)).thenReturn(Optional.of(NOTICE));
        when(subscriptions.findForResidentsWithReminders()).thenReturn(List.of(subscription(1, "fr")));
        when(sender.send(any(), any())).thenReturn(PushSender.Result.RETRYABLE);

        notifier.pushNotice(12L);

        verify(subscriptions, never()).deleteByEndpoint(any());
    }

    @Test
    void oneBrokenDeviceDoesNotStopTheRest() {
        when(notices.findById(12L)).thenReturn(Optional.of(NOTICE));
        when(subscriptions.findForResidentsWithReminders())
                .thenReturn(List.of(subscription(1, "fr"), subscription(2, "en")));
        when(sender.send(eq(subscription(1, "fr")), any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> notifier.pushNotice(12L)).doesNotThrowAnyException();
    }

    @Test
    void failureNeverReachesTheConsumerThatCalledIt() {
        // The inbox write already committed. Throwing from here would make
        // Kafka redeliver and write the inbox row again.
        when(notices.findById(12L)).thenThrow(new IllegalStateException("database is down"));

        assertThatCode(() -> notifier.pushNotice(12L)).doesNotThrowAnyException();
    }

    @Test
    void doesNothingAtAllWhenPushIsOff() {
        PushNotifier off = new PushNotifier(subscriptions, notices, sender, new ObjectMapper(), false);

        off.pushNotice(12L);

        verifyNoInteractions(subscriptions, notices, sender);
    }

    @Test
    void sendsNothingForANoticeThatNoLongerExists() {
        when(notices.findById(12L)).thenReturn(Optional.empty());

        notifier.pushNotice(12L);

        verifyNoInteractions(sender);
    }
}
