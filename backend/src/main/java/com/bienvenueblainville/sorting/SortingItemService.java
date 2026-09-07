package com.bienvenueblainville.sorting;

import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.sorting.dto.SortingItemRequest;
import com.bienvenueblainville.sorting.dto.SortingItemResponse;
import com.bienvenueblainville.sorting.dto.TranslationInput;
import com.bienvenueblainville.sorting.dto.TranslationView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

    public SortingItemService(
            SortingItemMapper itemMapper,
            SortingItemTranslationMapper translationMapper,
            SortingItemKeywordMapper keywordMapper
    ) {
        this.itemMapper = itemMapper;
        this.translationMapper = translationMapper;
        this.keywordMapper = keywordMapper;
    }

    public List<SortingItemResponse> all() {
        return itemMapper.findAll().stream().map(this::toResponse).toList();
    }

    public SortingItemResponse create(SortingItemRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("destinationType", request.destinationType());
        params.put("binColor", request.binColor());
        params.put("sourceUrl", request.sourceUrl());

        itemMapper.insert(params);
        Long itemId = (Long) params.get("id");

        saveTranslationsAndKeywords(itemId, request);
        return toResponse(requireItem(itemId));
    }

    public SortingItemResponse update(Long id, SortingItemRequest request) {
        requireItem(id);
        itemMapper.update(id, request.destinationType(), request.binColor(), request.sourceUrl());

        translationMapper.deleteByItemId(id);
        keywordMapper.deleteByItemId(id);
        saveTranslationsAndKeywords(id, request);

        return toResponse(requireItem(id));
    }

    public void delete(Long id) {
        requireItem(id);
        keywordMapper.deleteByItemId(id);
        translationMapper.deleteByItemId(id);
        itemMapper.delete(id);
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
        translationMapper.insert(itemId, languageCode, input.name(), input.instruction(), input.location());
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

    private SortingItemResponse toResponse(SortingItem item) {
        Map<LanguageCode, SortingItemTranslation> translations = translationMapper.findByItemId(item.id()).stream()
                .collect(Collectors.toMap(SortingItemTranslation::languageCode, t -> t));

        Map<LanguageCode, List<String>> keywords = keywordMapper.findByItemId(item.id()).stream()
                .collect(Collectors.groupingBy(
                        SortingItemKeyword::languageCode,
                        Collectors.mapping(SortingItemKeyword::keyword, Collectors.toList())
                ));

        return new SortingItemResponse(
                item.id(),
                item.destinationType(),
                item.binColor(),
                item.sourceUrl(),
                toView(translations.get(LanguageCode.fr)),
                toView(translations.get(LanguageCode.en)),
                toView(translations.get(LanguageCode.zh)),
                keywords.getOrDefault(LanguageCode.fr, Collections.emptyList()),
                keywords.getOrDefault(LanguageCode.en, Collections.emptyList()),
                keywords.getOrDefault(LanguageCode.zh, Collections.emptyList())
        );
    }

    private TranslationView toView(SortingItemTranslation translation) {
        if (translation == null) {
            return null;
        }
        return new TranslationView(translation.name(), translation.instruction(), translation.location());
    }
}
