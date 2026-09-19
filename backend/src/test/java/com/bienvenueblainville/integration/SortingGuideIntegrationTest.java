package com.bienvenueblainville.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The sorting guide now has one source, and this is what pins it there.
 *
 * <p>Before migration V10 the cards residents read came from a TypeScript file
 * bundled with the frontend, while the assistant, the photo lookup and the
 * admin console all read MySQL. An administrator could correct an entry and
 * the page everybody actually looks at would not change.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class SortingGuideIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theGuideIsPublicAndCarriesEveryLanguage() throws Exception {
        JsonNode guide = fetchGuide();

        assertThat(guide.isArray()).isTrue();
        assertThat(guide).isNotEmpty();
        assertThat(guide).allSatisfy(item -> {
            assertThat(item.get("fr").get("name").asText()).isNotBlank();
            assertThat(item.get("en").get("name").asText()).isNotBlank();
            assertThat(item.get("zh").get("name").asText()).isNotBlank();
        });
    }

    @Test
    void theExamplesThatUsedToLiveInTheFrontendSurvivedTheMove() throws Exception {
        // These were in sortingGuide.ts and nowhere else. Migration V11 carried
        // them over; if that had silently failed, the cards would render with
        // an empty hint list and nobody would have noticed until a resident did.
        JsonNode guide = fetchGuide();

        JsonNode withExamples = findByEnglishName(guide, "Food-soiled paper and cardboard");
        assertThat(withExamples.get("fr").get("examples")).isNotEmpty();
        assertThat(withExamples.get("en").get("examples")).isNotEmpty();
        assertThat(withExamples.get("zh").get("examples")).isNotEmpty();
    }

    @Test
    void examplesComeBackInTheOrderTheyWereEntered() throws Exception {
        // Stored with an explicit position, because the order an administrator
        // typed them in is the order a resident should read them.
        JsonNode item = findByEnglishName(fetchGuide(), "Fruit and vegetable scraps");
        JsonNode examples = item.get("en").get("examples");

        assertThat(examples.get(0).asText()).isEqualTo("fruit");
        assertThat(examples.get(1).asText()).isEqualTo("vegetables");
    }

    @Test
    void seasonalWordingSurvivedTheMoveToo() throws Exception {
        JsonNode branches = findByEnglishName(fetchGuide(), "Branches");

        assertThat(branches.get("fr").get("availability").asText()).isNotBlank();
        assertThat(branches.get("en").get("availability").asText()).isNotBlank();
    }

    @Test
    void theDuplicateShreddingEntryIsGoneAndKeptItsKeywords() throws Exception {
        // Rows 4 and 14 were the same municipal service seeded twice. Residents
        // saw two cards, and retrieval split the relevance score across both,
        // pushing the real entry down the results.
        Integer shreddingEntries = jdbc.queryForObject(
                "select count(*) from sorting_item_translation "
                        + "where language_code = 'en' and name like '%shredding%'",
                Integer.class);
        assertThat(shreddingEntries).isEqualTo(1);

        // The thin duplicate's search terms were merged in before it went, so
        // a resident searching the words it carried still finds the entry.
        Integer merged = jdbc.queryForObject(
                "select count(*) from sorting_item_keyword "
                        + "where item_id = 14 and keyword = 'personal documents'",
                Integer.class);
        assertThat(merged).isEqualTo(1);
    }

    @Test
    void theGuideMatchesWhatTheAssistantSearches() throws Exception {
        // The point of the whole migration: one dataset. If these two ever
        // diverged again, a resident could be shown a card the assistant
        // cannot find, or vice versa.
        int inGuide = fetchGuide().size();
        Integer inDatabase = jdbc.queryForObject("select count(*) from sorting_item", Integer.class);

        assertThat(inGuide).isEqualTo(inDatabase);
    }

    private JsonNode fetchGuide() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/sorting-items"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static JsonNode findByEnglishName(JsonNode guide, String name) {
        for (JsonNode item : guide) {
            if (name.equals(item.get("en").get("name").asText())) {
                return item;
            }
        }
        throw new AssertionError("No sorting item named " + name);
    }
}
