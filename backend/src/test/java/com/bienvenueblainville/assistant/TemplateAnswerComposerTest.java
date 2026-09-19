package com.bienvenueblainville.assistant;

import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.sorting.DestinationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateAnswerComposerTest {
    private final TemplateAnswerComposer composer = new TemplateAnswerComposer();

    @Test
    void leadsWithTheBestMatchInTheAskedLanguage() {
        String answer = composer.compose(
                new AssistantQuestion("boîte à pizza", LanguageCode.fr),
                List.of(item("Papiers et cartons souilles", "Dans le bac brun.", null),
                        item("Contenants et emballages", "Dans le bac bleu.", null)));

        assertThat(answer).startsWith("D'après le guide de tri de Blainville :");
        assertThat(answer).contains("Papiers et cartons souilles").contains("Dans le bac brun.");
        assertThat(answer).contains("Entrées connexes : Contenants et emballages");
    }

    @Test
    void includesTheDropOffLocationWhenTheEntryHasOne() {
        String answer = composer.compose(
                new AssistantQuestion("peinture", LanguageCode.en),
                List.of(item("Items to bring to the ecocentre", "Bring it to the ecocentre.",
                        "302 rue Omer-DeSerres")));

        assertThat(answer).contains("302 rue Omer-DeSerres");
    }

    @Test
    void answersChineseInChinese() {
        String answer = composer.compose(
                new AssistantQuestion("废电池", LanguageCode.zh),
                List.of(item("需要送去 ecocentre 的物品", "请送去 ecocentre。", null)));

        assertThat(answer).startsWith("根据 Blainville 分类指南：");
    }

    private static RetrievedItem item(String name, String instruction, String location) {
        return new RetrievedItem(1L, LanguageCode.fr, name, instruction, location,
                DestinationType.organic, BinColor.brown, null, 1.0);
    }
}
