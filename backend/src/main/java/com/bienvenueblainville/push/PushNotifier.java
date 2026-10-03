package com.bienvenueblainville.push;

import com.bienvenueblainville.notice.SpecialNotice;
import com.bienvenueblainville.notice.SpecialNoticeMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Sends a new notice to the devices that asked to be told.
 *
 * <p>Second delivery, never the only one. {@code resident_notification} is
 * written first and is the system of record; everything here can fail - a
 * revoked permission, an unreachable push service, a device that has not been
 * opened in a month - without a resident losing the notice. That is why
 * nothing in this class throws into the Kafka consumer that calls it.
 */
public class PushNotifier {
    private static final Logger log = LoggerFactory.getLogger(PushNotifier.class);

    private final PushSubscriptionMapper subscriptions;
    private final SpecialNoticeMapper notices;
    private final PushSender sender;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public PushNotifier(
            PushSubscriptionMapper subscriptions,
            SpecialNoticeMapper notices,
            PushSender sender,
            ObjectMapper objectMapper,
            boolean enabled
    ) {
        this.subscriptions = subscriptions;
        this.notices = notices;
        this.sender = sender;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public void pushNotice(Long noticeId) {
        if (!enabled) {
            return;
        }
        try {
            SpecialNotice notice = notices.findById(noticeId).orElse(null);
            if (notice == null) {
                return;
            }
            List<PushSubscription> targets = subscriptions.findForResidentsWithReminders();
            int sent = 0;
            int dropped = 0;
            for (PushSubscription subscription : targets) {
                PushSender.Result result = sender.send(subscription, payloadFor(notice, subscription.languageCode()));
                if (result == PushSender.Result.SENT) {
                    sent++;
                } else if (result == PushSender.Result.GONE) {
                    subscriptions.deleteByEndpoint(subscription.endpoint());
                    dropped++;
                }
            }
            log.info("Notice {} pushed to {} of {} device(s); {} expired subscription(s) removed",
                    noticeId, sent, targets.size(), dropped);
        } catch (RuntimeException e) {
            // The inbox already has it. A push that fails is a worse
            // notification, not a lost one, and must not fail the consumer.
            log.warn("Could not push notice {}", noticeId, e);
        }
    }

    /**
     * What the service worker receives. Deliberately small: a push payload is
     * capped (4 KB in practice) and this one only has to be enough to show a
     * notification and open the right page.
     */
    private String payloadFor(SpecialNotice notice, String language) {
        String title = switch (language) {
            case "en" -> notice.titleEn();
            case "zh" -> notice.titleZh();
            default -> notice.titleFr();
        };
        String body = switch (language) {
            case "en" -> notice.bodyEn();
            case "zh" -> notice.bodyZh();
            default -> notice.bodyFr();
        };
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "title", title == null ? "" : title,
                    "body", body == null ? "" : body,
                    "noticeId", notice.id()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not build the push payload", e);
        }
    }
}
