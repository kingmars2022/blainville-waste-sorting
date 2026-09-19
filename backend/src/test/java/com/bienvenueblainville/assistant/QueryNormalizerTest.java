package com.bienvenueblainville.assistant;

import com.bienvenueblainville.common.LanguageCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryNormalizerTest {

    @Test
    void stripsTheFrenchFunctionWordThatMadeTheAssistantAnswerAnUnanswerableQuestion() {
        // The regression this class was written for: MySQL's built-in stopword
        // list is English-only, so "est" was indexed as content and matched the
        // household-waste entry, turning a question about the mayor's phone
        // number into bin advice.
        String normalized = QueryNormalizer.normalize(
                "Quel est le numéro de téléphone du maire ?", LanguageCode.fr);

        assertThat(normalized).doesNotContain("est");
        assertThat(normalized).contains("numéro").contains("téléphone").contains("maire");
    }

    @Test
    void keepsWordsThatCarrySortingMeaning() {
        assertThat(QueryNormalizer.normalize("Où va une boîte à pizza sale ?", LanguageCode.fr))
                .contains("boîte").contains("pizza").contains("sale");
    }

    @Test
    void foldsAccentsWhenMatchingStopwordsButNotWhenEmittingThem() {
        // "Où" has to be recognised as the stopword "ou"...
        assertThat(QueryNormalizer.normalize("Où mettre les branches ?", LanguageCode.fr))
                .doesNotContain("Où").doesNotContain("les");
        // ...while accented content words reach MySQL unchanged, since its
        // collation does its own accent handling.
        assertThat(QueryNormalizer.normalize("Où jeter une éponge ?", LanguageCode.fr))
                .contains("éponge");
    }

    @Test
    void stripsEnglishFunctionWords() {
        assertThat(QueryNormalizer.normalize("Where does the cardboard go?", LanguageCode.en))
                .isEqualTo("cardboard");
    }

    @Test
    void leavesChineseUntouchedBecauseItIsSearchedWithoutWordBoundaries() {
        String question = "废电池怎么处理？";
        assertThat(QueryNormalizer.normalize(question, LanguageCode.zh)).isEqualTo(question);
    }

    @Test
    void returnsBlankWhenNothingMeaningfulSurvives() {
        // Which the retriever turns into a refusal rather than a broad search.
        assertThat(QueryNormalizer.normalize("Quel est le plus ?", LanguageCode.fr)).isBlank();
    }
}
