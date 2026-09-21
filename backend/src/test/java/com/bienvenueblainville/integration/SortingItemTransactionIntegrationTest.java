package com.bienvenueblainville.integration;

import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.sorting.DestinationType;
import com.bienvenueblainville.sorting.SortingItemService;
import com.bienvenueblainville.sorting.dto.SortingItemRequest;
import com.bienvenueblainville.sorting.dto.TranslationInput;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One sorting item is up to ten inserts. Either all of them happen or none.
 *
 * <p>Creating an entry writes the row, then a translation, a keyword list and
 * an example list for each of three languages. Without a transaction a
 * failure part-way through leaves exactly the thing the API contract says
 * cannot exist — an entry missing some of its languages — and nothing that
 * says which one, because the request already returned an error about
 * something else.
 *
 * <p>The failure here is a real one rather than a mock: a name longer than
 * the column, which passes bean validation (there is no max length on it) and
 * is refused by MySQL. That is also a fair model of the accident this guards
 * against, which is data-shaped rather than logic-shaped.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
class SortingItemTransactionIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
    }

    @Autowired
    private SortingItemService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aCreateThatFailsPartWayLeavesNothingBehind() {
        int itemsBefore = count("sorting_item");
        int translationsBefore = count("sorting_item_translation");

        // 300 characters into varchar(255). The parent row and the French
        // translation insert before this one succeeds.
        String tooLong = "x".repeat(300);

        assertThatThrownBy(() -> service.create(new SortingItemRequest(
                DestinationType.organic, BinColor.brown, "https://blainville.ca/rollback-test",
                new TranslationInput("Valide", "Instruction", null, null, List.of("un")),
                new TranslationInput(tooLong, "Instruction", null, null, List.of("one")),
                new TranslationInput("有效", "说明", null, null, List.of("一")),
                List.of("fr"), List.of("en"), List.of("zh"))))
                .isInstanceOf(Exception.class);

        assertThat(count("sorting_item"))
                .as("the parent row must not survive a failed create")
                .isEqualTo(itemsBefore);
        assertThat(count("sorting_item_translation"))
                .as("nor the translations that were written before the failure")
                .isEqualTo(translationsBefore);
        assertThat(jdbc.queryForObject(
                "select count(*) from sorting_item where source_url = ?",
                Integer.class, "https://blainville.ca/rollback-test"))
                .isZero();
    }

    @Test
    void aSuccessfulCreateStillWritesEveryChildRow() {
        // The other half: a transaction that rolls back everything is only
        // correct if the committing path still commits all of it.
        var created = service.create(new SortingItemRequest(
                DestinationType.recycling, BinColor.blue, "https://blainville.ca/commit-test",
                new TranslationInput("Bouteille", "Bac bleu", null, "Toute l'annee", List.of("verre", "plastique")),
                new TranslationInput("Bottle", "Blue bin", null, "Year round", List.of("glass")),
                new TranslationInput("瓶子", "蓝桶", null, "全年", List.of("玻璃")),
                List.of("bouteille"), List.of("bottle"), List.of("瓶子")));

        try {
            assertThat(childCount("sorting_item_translation", created.id())).isEqualTo(3);
            assertThat(childCount("sorting_item_keyword", created.id())).isEqualTo(3);
            assertThat(childCount("sorting_item_example", created.id())).isEqualTo(4);
        } finally {
            service.delete(created.id());
        }

        assertThat(childCount("sorting_item_translation", created.id()))
                .as("and delete takes the children with it")
                .isZero();
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private int childCount(String table, Long itemId) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " where item_id = ?", Integer.class, itemId);
    }
}
