package com.bienvenueblainville.assistant;

import com.bienvenueblainville.assistant.dto.AssistantAnswer;
import com.bienvenueblainville.common.LanguageCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Remembers what the assistant already answered, so asking the same thing twice
 * does not pay for it twice.
 *
 * <p>Two residents asking "où va une boîte à pizza ?" in the same week is the
 * normal case, not an edge case — it is a municipal sorting guide, and the
 * questions concentrate hard on the twenty materials people are unsure about.
 * Every repeat currently re-runs retrieval and, with Claude configured, buys
 * another model call to produce wording that is already known.
 *
 * <p><b>The cache key is the interesting part.</b> It has to include:
 * <ul>
 *   <li>the <b>normalized</b> question, so "Boîte à pizza ?" and "boite a
 *       pizza" are one entry rather than two;</li>
 *   <li>the <b>language</b>, because the same words can appear in two of
 *       them;</li>
 *   <li>the <b>composer</b>, because switching from the template to Claude
 *       must not keep serving the old wording.</li>
 * </ul>
 *
 * <p>And it has to be thrown away when an administrator edits the sorting
 * guide. A cached refusal for "aquarium" that survives somebody adding the
 * aquarium entry is worse than no cache: the guide would be right and the
 * assistant would still be saying it does not know.
 */
@Component
public class AnswerCache {
    private static final Logger log = LoggerFactory.getLogger(AnswerCache.class);

    /** Public so operators and tests can find these keys without guessing the shape. */
    public static final String PREFIX = "assistant:answer:";

    /**
     * A day. The eviction on guide edits is what keeps answers correct; this
     * only bounds how long a stale entry could survive an eviction that failed.
     */
    static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public AnswerCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<AssistantAnswer> find(String question, LanguageCode language, String provider) {
        try {
            String cached = redis.opsForValue().get(key(question, language, provider));
            return cached == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(cached, AssistantAnswer.class));
        } catch (Exception e) {
            // A cache is an optimisation. Losing Redis, or finding a value this
            // version cannot read, means doing the work - never failing.
            log.debug("Could not read a cached answer", e);
            return Optional.empty();
        }
    }

    public void store(String question, LanguageCode language, String provider, AssistantAnswer answer) {
        try {
            redis.opsForValue().set(
                    key(question, language, provider),
                    objectMapper.writeValueAsString(answer),
                    TTL);
        } catch (Exception e) {
            log.debug("Could not cache an answer", e);
        }
    }

    /**
     * Called when the sorting guide changes.
     *
     * <p>Everything, not a targeted key: an edit to one entry changes which
     * entry wins for an unknown number of questions, and a new entry changes
     * the answer to questions that previously had none. There is no way to
     * know which cached answers an edit invalidates without re-running them
     * all, so the whole set goes.
     */
    public void invalidateAll() {
        try {
            var keys = redis.keys(PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
                log.info("Sorting guide changed: dropped {} cached answer(s)", keys.size());
            }
        } catch (Exception e) {
            log.warn("Could not clear cached answers; they will expire within {}", TTL, e);
        }
    }

    /**
     * Hashed rather than embedded: a resident's question can be long, can
     * contain anything they typed, and would otherwise end up legible in
     * whatever tooling lists Redis keys.
     */
    private String key(String question, LanguageCode language, String provider) {
        String normalized = QueryNormalizer.normalize(question, language).toLowerCase(java.util.Locale.ROOT);
        String material = language.name() + "|" + provider + "|" + normalized;

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
