package com.bienvenueblainville.config;

import com.bienvenueblainville.collection.CollectionEvent;
import com.bienvenueblainville.notice.SpecialNotice;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;

/**
 * Redis caching for the two endpoints every resident hits on page load:
 * the upcoming collection schedule and the active notices.
 *
 * <p>Three decisions here are worth more than the code:
 *
 * <ol>
 *   <li><b>The cache is optional.</b> It is a read-through cache in front of
 *       MySQL, never a source of truth, so a Redis outage must degrade the
 *       site to "slower" and not to "down". That is what {@link #errorHandler()}
 *       buys: every Redis failure is logged and swallowed, and the request
 *       falls through to the database. Run without Redis entirely by setting
 *       {@code CACHE_TYPE=none}.</li>
 *   <li><b>Values are serialized as plain JSON of a known type.</b> Spring's
 *       default {@code GenericJackson2JsonRedisSerializer} writes polymorphic
 *       {@code @class} hints, which do not round-trip reliably for Java
 *       {@code record}s (they are final, so default typing skips them and the
 *       elements come back as {@code LinkedHashMap}). Declaring the exact
 *       {@code List<CollectionEvent>} / {@code List<SpecialNotice>} type per
 *       cache avoids that entirely and keeps the stored values readable in
 *       {@code redis-cli} — useful when debugging a stale entry.</li>
 *   <li><b>Nulls are not cached.</b> A typed serializer cannot represent
 *       Spring's {@code NullValue} marker; neither cached method returns null
 *       (both return an empty list), so caching nulls is disabled rather than
 *       left as a latent serialization failure.</li>
 * </ol>
 *
 * <p>TTLs are a safety net, not the correctness mechanism: admin writes evict
 * the affected cache immediately via {@code @CacheEvict}. The TTL only bounds
 * how long a cache that somehow missed an eviction can stay wrong.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Keyed by day + sector + horizon; see {@code CollectionService.upcoming}. */
    public static final String UPCOMING_COLLECTIONS = "collections:upcoming";

    /** Keyed by day; see {@code SpecialNoticeService.active}. */
    public static final String ACTIVE_NOTICES = "notices:active";

    @Bean
    public RedisCacheManagerBuilderCustomizer blainvilleCacheCustomizer() {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        return builder -> builder
                .withCacheConfiguration(
                        UPCOMING_COLLECTIONS,
                        listCache(mapper, CollectionEvent.class, Duration.ofMinutes(10)))
                .withCacheConfiguration(
                        ACTIVE_NOTICES,
                        listCache(mapper, SpecialNotice.class, Duration.ofMinutes(5)));
    }

    private RedisCacheConfiguration listCache(ObjectMapper mapper, Class<?> elementType, Duration ttl) {
        JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(mapper, listType)));
    }

    /**
     * Makes a Redis outage cheap instead of merely survivable.
     *
     * <p>Measured, not assumed: with Lettuce's default
     * {@code DisconnectedBehavior.DEFAULT} a command issued while the
     * connection is down is <em>queued</em> until the command timeout expires.
     * Killing Redis under a running server turned every home-page request
     * into a 4-second one — 2s for the failed cache read plus 2s for the
     * failed cache write — even though the error handler below was already
     * swallowing both failures. The site stayed up and served correct data,
     * but four seconds a request is an outage in everything but name.
     *
     * <p>{@code REJECT_COMMANDS} makes those commands fail immediately
     * instead of waiting, so a dead Redis costs microseconds and the request
     * falls straight through to MySQL. {@code autoReconnect} still brings the
     * cache back by itself once Redis returns.
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer failFastWhenRedisIsDown() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofMillis(500))
                        .build())
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(500)))
                .build());
    }

    /**
     * Turns every Redis failure into a log line instead of a 500. Spring's
     * default handler rethrows, which would make Redis a hard dependency of
     * the home page — exactly the coupling a cache is supposed to avoid.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache read failed ({} / {}), falling through to the database", cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache write failed ({} / {}), response already served", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                // The write itself already committed; the cache will self-correct
                // at its TTL, so this must not fail the admin request.
                log.warn("Cache evict failed ({} / {}), entry will expire on its TTL", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache clear failed ({}), entries will expire on their TTL", cache.getName(), exception);
            }
        };
    }
}
