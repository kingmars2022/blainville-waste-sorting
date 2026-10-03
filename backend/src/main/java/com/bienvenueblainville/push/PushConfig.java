package com.bienvenueblainville.push;

import com.bienvenueblainville.notice.SpecialNoticeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * Push is optional infrastructure, in the same way Kafka and MongoDB are here:
 * {@code app.push.enabled=false} - the default - leaves the resident inbox
 * working and sends nothing.
 *
 * <p>Enabling it without keys is refused at startup rather than at the first
 * notice. A feature that looks on and silently is not is the failure mode this
 * project keeps running into.
 */
@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {
    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    public PushNotifier pushNotifier(
            PushProperties properties,
            PushSubscriptionMapper subscriptions,
            SpecialNoticeMapper notices,
            ObjectMapper objectMapper
    ) {
        if (!properties.enabled()) {
            log.info("Web push is off (app.push.enabled=false); notices still reach the resident inbox");
            return new PushNotifier(subscriptions, notices, null, objectMapper, false);
        }
        if (isBlank(properties.publicKey()) || isBlank(properties.privateKey()) || isBlank(properties.subject())) {
            throw new IllegalStateException(
                    "app.push.enabled=true needs app.push.public-key, app.push.private-key and app.push.subject. "
                            + "Generate a key pair with VapidKeyGenerator.");
        }

        VapidAuthentication vapid = new VapidAuthentication(
                VapidKeys.of(properties.publicKey(), properties.privateKey()),
                properties.subject(),
                Clock.systemUTC());
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return new PushNotifier(subscriptions, notices, new PushSender(http, vapid), objectMapper, true);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
