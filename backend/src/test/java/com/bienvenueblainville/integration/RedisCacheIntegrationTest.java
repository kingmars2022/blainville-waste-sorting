package com.bienvenueblainville.integration;

import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.collection.CollectionEvent;
import com.bienvenueblainville.collection.CollectionService;
import com.bienvenueblainville.collection.CollectionType;
import com.bienvenueblainville.collection.dto.CollectionEventRequest;
import com.bienvenueblainville.common.Sector;
import com.bienvenueblainville.config.CacheConfig;
import com.bienvenueblainville.notice.SpecialNotice;
import com.bienvenueblainville.notice.SpecialNoticeService;
import com.bienvenueblainville.notice.dto.NoticeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Redis cache actually caches, actually keys by day, and actually
 * gets evicted by admin writes — against a real Redis and a real MySQL.
 *
 * <p>The interesting trick here is how a cache hit is detected without mocks:
 * the tests change the database <em>behind</em> the service, with a plain
 * {@link JdbcTemplate}, and then assert the service still returns the old
 * answer. Only a real cache hit can produce that. A test that just called the
 * method twice and compared the results would pass whether or not anything
 * was cached at all.
 *
 * <p>Requires a Redis on {@code REDIS_HOST}/{@code REDIS_PORT} (default
 * localhost:6379) in addition to the MySQL the rest of the integration suite
 * needs. Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
class RedisCacheIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        // This class exists to test the cache, so it pins the cache on even if
        // a developer has CACHE_TYPE=none in their shell.
        registry.add("CACHE_TYPE", () -> "redis");
    }

    private static final String MARKER = "redis-cache-integration-test";

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private SpecialNoticeService noticeService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void reset() {
        jdbc.update("delete from collection_event where source_url = ?", MARKER);
        jdbc.update("delete from special_notice where source_url = ?", MARKER);
        cacheManager.getCache(CacheConfig.UPCOMING_COLLECTIONS).clear();
        cacheManager.getCache(CacheConfig.ACTIVE_NOTICES).clear();
    }

    @Test
    void upcomingCollectionsAreServedFromRedisOnTheSecondCall() {
        List<CollectionEvent> first = collectionService.upcoming(Sector.north, 30);

        // A row appears in MySQL that the service never saw go in.
        insertCollectionRow(LocalDate.now().plusDays(1));

        List<CollectionEvent> second = collectionService.upcoming(Sector.north, 30);

        // Identical despite the new row: the second call never reached MySQL.
        assertThat(second).isEqualTo(first);
        assertThat(second).noneMatch(event -> MARKER.equals(event.sourceUrl()));
    }

    @Test
    void theCacheKeyIsScopedByDaySectorAndHorizon() {
        collectionService.upcoming(Sector.north, 30);
        collectionService.upcoming(Sector.south, 30);
        collectionService.upcoming(Sector.north, 7);

        Set<String> keys = redis.keys(CacheConfig.UPCOMING_COLLECTIONS + "::*");
        assertThat(keys).containsExactlyInAnyOrder(
                key(CacheConfig.UPCOMING_COLLECTIONS, LocalDate.now() + ":north:30"),
                key(CacheConfig.UPCOMING_COLLECTIONS, LocalDate.now() + ":south:30"),
                key(CacheConfig.UPCOMING_COLLECTIONS, LocalDate.now() + ":north:7"));
    }

    @Test
    void cachedValuesRoundTripThroughJsonWithTheirDatesIntact() {
        List<CollectionEvent> fromDatabase = collectionService.upcoming(Sector.north, 30);
        cacheManager.getCache(CacheConfig.UPCOMING_COLLECTIONS).clear();
        collectionService.upcoming(Sector.north, 30);

        // Second read comes back out of Redis as JSON. Records, enums and
        // LocalDate all have to survive that; an ISO date string rather than
        // a timestamp array is what JavaTimeModule buys.
        List<CollectionEvent> fromRedis = collectionService.upcoming(Sector.north, 30);
        assertThat(fromRedis).isEqualTo(fromDatabase);

        String raw = redis.opsForValue().get(key(CacheConfig.UPCOMING_COLLECTIONS, LocalDate.now() + ":north:30"));
        assertThat(raw).isNotNull();
        if (!fromDatabase.isEmpty()) {
            assertThat(raw).contains("\"" + fromDatabase.get(0).collectionDate() + "\"");
        }
    }

    @Test
    void anAdminWriteEvictsTheCollectionCache() {
        collectionService.upcoming(Sector.north, 30);
        assertThat(redis.keys(CacheConfig.UPCOMING_COLLECTIONS + "::*")).isNotEmpty();

        collectionService.create(new CollectionEventRequest(
                LocalDate.now().plusDays(2),
                Sector.north,
                CollectionType.garbage,
                BinColor.black,
                null, null, null,
                MARKER));

        assertThat(redis.keys(CacheConfig.UPCOMING_COLLECTIONS + "::*")).isEmpty();
        assertThat(collectionService.upcoming(Sector.north, 30))
                .anyMatch(event -> MARKER.equals(event.sourceUrl()));
    }

    @Test
    void publishingANoticeEvictsTheNoticeCache() {
        List<SpecialNotice> before = noticeService.active();
        assertThat(redis.keys(CacheConfig.ACTIVE_NOTICES + "::*")).isNotEmpty();

        noticeService.create(new NoticeRequest(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                "Avis de test", "Test notice", "测试通知",
                "Corps", "Body", "正文",
                MARKER,
                true));

        assertThat(redis.keys(CacheConfig.ACTIVE_NOTICES + "::*")).isEmpty();
        assertThat(noticeService.active()).hasSize(before.size() + 1);
    }

    private String key(String cacheName, String suffix) {
        return cacheName + "::" + suffix;
    }

    private void insertCollectionRow(LocalDate date) {
        jdbc.update("insert into collection_event"
                        + " (collection_date, sector, collection_type, bin_color, source_url)"
                        + " values (?, ?, ?, ?, ?)",
                date, Sector.north.name(), CollectionType.garbage.name(), BinColor.black.name(), MARKER);
    }

}
