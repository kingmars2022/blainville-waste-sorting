package com.bienvenueblainville.assistant;

import com.bienvenueblainville.common.LanguageCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SortingSearchMapper {
    /**
     * Ranked full-text search over one language's sorting guide.
     *
     * @param languageCode which language's rows to search; the guide is stored
     *                     per language, so this is a filter, not a hint
     * @param query        the resident's own words, passed as a bind parameter -
     *                     never interpolated into the SQL
     * @param limit        hard cap on rows returned, so a broad query cannot
     *                     drag the whole guide into an LLM prompt
     * @param ngram        true to match against the ngram-parsed columns
     *                     (Chinese), false for the word-parsed ones (French,
     *                     English). See V7 for why this is not one index.
     */
    List<RetrievedItem> search(
            @Param("languageCode") LanguageCode languageCode,
            @Param("query") String query,
            @Param("limit") int limit,
            @Param("ngram") boolean ngram
    );
}
