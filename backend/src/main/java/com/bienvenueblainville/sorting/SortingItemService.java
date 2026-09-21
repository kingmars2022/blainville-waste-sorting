package com.bienvenueblainville.sorting;

import com.bienvenueblainville.assistant.AnswerCache;
import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.sorting.dto.SortingItemRequest;
import com.bienvenueblainville.sorting.dto.SortingItemResponse;
import com.bienvenueblainville.sorting.dto.TranslationInput;
import com.bienvenueblainville.sorting.dto.TranslationView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SortingItemService {
    private final SortingItemMapper itemMapper;
    private final SortingItemTranslationMapper translationMapper;
    private final SortingItemKeywordMapper keywordMapper;
    private final SortingItemExampleMapper exampleMapper;
    private final AnswerCache answerCache;

    public SortingItemService(
            SortingItemMapper itemMapper,
            SortingItemTranslationMapper translationMapper,
            SortingItemKeywordMapper keywordMapper,
            SortingItemExampleMapper exampleMapper,
            AnswerCache answerCache
    ) {
        this.itemMapper = itemMapper;
        this.translationMapper = translationMapper;
        this.keywordMapper = keywordMapper;
        this.exampleMapper = exampleMapper;
        this.answerCache = answerCache;
    }

    /**
     * The whole guide, for the admin console.
     *
     * <p>Four queries, not one per item. It used to map each row through
     * {@link #toResponse(SortingItem)}, which costs three lookups of its own:
     * fourteen entries meant forty-three queries every time an administrator
     * opened the page, and the count grew with the guide.
     *
     * <p>{@link #guide()} serves residents and was already batched. This one
     * was not, which is the usual shape of the bug - the path someone
     * measured is fast and the one beside it never got the same treatment.
     */
    public List<SortingItemResponse> all() {
        List<SortingItem> items = itemMapper.findAll();
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> ids = items.stream().map(SortingItem::id).toList();
        Map<Long, List<SortingItemTranslation>> translations =
                byItem(translationMapper.findByItemIds(ids), SortingItemTranslation::itemId);
        Map<Long, List<SortingItemKeyword>> keywords =
                byItem(keywordMapper.findByItemIds(ids), SortingItemKeyword::itemId);
        Map<Long, List<SortingItemExample>> examples =
                byItem(exampleMapper.findByItemIds(ids), SortingItemExample::itemId);

        return items.stream()
                .map(item -> toResponse(
                        item,
                        translations.getOrDefault(item.id(), List.of()),
                        keywords.getOrDefault(item.id(), List.of()),
                        examples.getOrDefault(item.id(), List.of())))
                .toList();
    }

    private static <T> Map<Long, List<T>> byItem(List<T> rows, java.util.function.Function<T, Long> itemId) {
        return rows.stream().collect(Collectors.groupingBy(itemId));
    }

    /**
     * One item is up to ten inserts: the row, then a translation, a keyword
     * list and an example list for each of three languages. Without a
     * transaction a failure part-way leaves an entry the API contract says
     * cannot exist - one with some of its languages missing - and no error
     * that says which.
     */
    @Transactional
    public SortingItemResponse create(SortingItemRequest request) {
        // A new entry changes the answer to questions that previously had
        // none, so every cached refusal is now potentially wrong.

        Map<String, Object> params = new HashMap<>();
        params.put("destinationType", request.destinationType());
        params.put("binColor", request.binColor());
        params.put("sourceUrl", request.sourceUrl());

        itemMapper.insert(params);
        Long itemId = ((Number) params.get("id")).longValue();

        saveTranslationsAndKeywords(itemId, request);
        answerCache.invalidateAll();
        return toResponse(requireItem(itemId));
    }

    @Transactional
    public SortingItemResponse update(Long id, SortingItemRequest request) {
        // An edit changes which entry wins for an unknown set of questions.

        requireItem(id);
        itemMapper.update(id, request.destinationType(), request.binColor(), request.sourceUrl());

        // Examples go with their translations - deleting one without the other
        // leaves orphan rows that would reappear under the next edit.
        exampleMapper.deleteByItemId(id);
        translationMapper.deleteByItemId(id);
        keywordMapper.deleteByItemId(id);
        saveTranslationsAndKeywords(id, request);
        answerCache.invalidateAll();

        return toResponse(requireItem(id));
    }

    /** Four deletes; a partial one orphans the children it did not reach. */
    @Transactional
    public void delete(Long id) {
        // A deletion can turn a cached answer into one citing a row that no
        // longer exists.

        requireItem(id);
        keywordMapper.deleteByItemId(id);
        exampleMapper.deleteByItemId(id);
        translationMapper.deleteByItemId(id);
        itemMapper.delete(id);
        answerCache.invalidateAll();
    }

    private void saveTranslationsAndKeywords(Long itemId, SortingItemRequest request) {
        insertTranslation(itemId, LanguageCode.fr, request.fr());
        insertTranslation(itemId, LanguageCode.en, request.en());
        insertTranslation(itemId, LanguageCode.zh, request.zh());

        insertKeywords(itemId, LanguageCode.fr, request.keywordsFr());
        insertKeywords(itemId, LanguageCode.en, request.keywordsEn());
        insertKeywords(itemId, LanguageCode.zh, request.keywordsZh());
    }

    private void insertTranslation(Long itemId, LanguageCode languageCode, TranslationInput input) {
        translationMapper.insert(itemId, languageCode, input.name(), input.instruction(),
                input.location(), input.availability());
        insertExamples(itemId, languageCode, input.examples());
    }

    private void insertExamples(Long itemId, LanguageCode languageCode, List<String> examples) {
        List<String> cleaned = examples == null
                ? List.of()
                : examples.stream().map(String::trim).filter(e -> !e.isEmpty()).toList();

        if (!cleaned.isEmpty()) {
            exampleMapper.insertBatch(itemId, languageCode, cleaned);
        }
    }

    private void insertKeywords(Long itemId, LanguageCode languageCode, List<String> keywords) {
        List<String> cleaned = keywords == null
                ? List.of()
                : keywords.stream().map(String::trim).filter(k -> !k.isEmpty()).toList();

        if (!cleaned.isEmpty()) {
            keywordMapper.insertBatch(itemId, languageCode, cleaned);
        }
    }

    private SortingItem requireItem(Long id) {
        return itemMapper.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Sorting item " + id + " not found"));
    }

    /**
     * The whole guide, for residents.
     *
     * <p>Four queries in total rather than four per item: at 15 items and three
     * languages the per-item version is 45 extra round trips to render one
     * page. This is the read that replaced the frontend's static copy of the
     * guide, so it is the one that has to be cheap.
     */
    public List<SortingItemResponse> guide() {
        Map<Long, List<SortingItemTranslation>> translations = translationMapper.findAll().stream()
                .collect(Collectors.groupingBy(SortingItemTranslation::itemId));
        Map<Long, List<SortingItemKeyword>> keywords = keywordMapper.findAll().stream()
                .collect(Collectors.groupingBy(SortingItemKeyword::itemId));
        Map<Long, List<SortingItemExample>> examples = exampleMapper.findAll().stream()
                .collect(Collectors.groupingBy(SortingItemExample::itemId));

        return itemMapper.findAll().stream()
                .map(item -> toResponse(
                        item,
                        translations.getOrDefault(item.id(), List.of()),
                        keywords.getOrDefault(item.id(), List.of()),
                        examples.getOrDefault(item.id(), List.of())))
                .toList();
    }

    private SortingItemResponse toResponse(SortingItem item) {
        return toResponse(
                item,
                translationMapper.findByItemId(item.id()),
                keywordMapper.findByItemId(item.id()),
                exampleMapper.findByItemId(item.id()));
    }

    private SortingItemResponse toResponse(
            SortingItem item,
            List<SortingItemTranslation> translationRows,
            List<SortingItemKeyword> keywordRows,
            List<SortingItemExample> exampleRows
    ) {
        Map<LanguageCode, SortingItemTranslation> translations = translationRows.stream()
                .collect(Collectors.toMap(SortingItemTranslation::languageCode, t -> t, (a, b) -> a));

        Map<LanguageCode, List<String>> keywords = keywordRows.stream()
                .collect(Collectors.groupingBy(
                        SortingItemKeyword::languageCode,
                        Collectors.mapping(SortingItemKeyword::keyword, Collectors.toList())
                ));

        Map<LanguageCode, List<String>> examples = exampleRows.stream()
                .sorted(java.util.Comparator.comparingInt(SortingItemExample::position))
                .collect(Collectors.groupingBy(
                        SortingItemExample::languageCode,
                        Collectors.mapping(SortingItemExample::example, Collectors.toList())
                ));

        return new SortingItemResponse(
                item.id(),
                item.destinationType(),
                item.binColor(),
                item.sourceUrl(),
                toView(translations.get(LanguageCode.fr), examples.get(LanguageCode.fr)),
                toView(translations.get(LanguageCode.en), examples.get(LanguageCode.en)),
                toView(translations.get(LanguageCode.zh), examples.get(LanguageCode.zh)),
                keywords.getOrDefault(LanguageCode.fr, Collections.emptyList()),
                keywords.getOrDefault(LanguageCode.en, Collections.emptyList()),
                keywords.getOrDefault(LanguageCode.zh, Collections.emptyList())
        );
    }

    private TranslationView toView(SortingItemTranslation translation, List<String> examples) {
        if (translation == null) {
            return null;
        }
        return new TranslationView(
                translation.name(),
                translation.instruction(),
                translation.location(),
                translation.availability(),
                examples == null ? Collections.emptyList() : examples);
    }
}
