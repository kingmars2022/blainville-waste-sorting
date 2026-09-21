package com.bienvenueblainville.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paging the collection schedule, against the real database.
 *
 * <p>The admin console listed every row, which was reasonable when the
 * calendar was a short hand-written seed and stopped being reasonable when
 * V12 made it extend itself to a rolling horizon without ever pruning what is
 * behind. The table only grows.
 *
 * <p>What is worth testing is not that a page has 25 rows — it is that the
 * pages are a partition of the list: no row on two pages, no row on none.
 * An unstable sort produces exactly that bug and looks fine on any single
 * page.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class CollectionPagingIntegrationTest {

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
    void everyRowAppearsOnExactlyOnePage() throws Exception {
        String token = adminToken();
        long total = jdbc.queryForObject("select count(*) from collection_event", Long.class);
        int size = 7;

        List<Integer> seen = new ArrayList<>();
        for (int page = 0; page * size < total; page++) {
            JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/admin/collections")
                            .param("page", String.valueOf(page))
                            .param("size", String.valueOf(size))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value((int) total))
                    .andReturn().getResponse().getContentAsString());

            body.get("items").forEach(item -> seen.add(item.get("id").asInt()));
        }

        assertThat(seen).hasSize((int) total);
        // The partition: every id once, none twice, none missing. A tie on
        // collection_date with no further ordering would break this without
        // any single page looking wrong.
        assertThat(seen).doesNotHaveDuplicates();
        assertThat(seen).containsExactlyInAnyOrderElementsOf(
                jdbc.queryForList("select id from collection_event", Integer.class));
    }

    @Test
    void reportsTheTotalRatherThanThePageLength() throws Exception {
        long total = jdbc.queryForObject("select count(*) from collection_event", Long.class);

        mockMvc.perform(get("/api/admin/collections")
                        .param("size", "3")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.page").value(0))
                // "1-3 of 118" needs the 118, which the page itself cannot say.
                .andExpect(jsonPath("$.total").value((int) total));
    }

    @Test
    void aPageBeyondTheEndIsEmptyRatherThanAnError() throws Exception {
        mockMvc.perform(get("/api/admin/collections")
                        .param("page", "9999")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void refusesAPageSizeThatWouldFetchTheWholeTable() throws Exception {
        // The point of paging is that the query is bounded. A size the caller
        // picks freely is not bounded, however politely it is asked for.
        mockMvc.perform(get("/api/admin/collections")
                        .param("size", "100000")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/collections")
                        .param("page", "-1")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stillRefusesAResidentEntirely() throws Exception {
        mockMvc.perform(get("/api/admin/collections").param("size", "5"))
                .andExpect(status().isUnauthorized());
    }

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", IntegrationEnvironment.ADMIN_EMAIL,
                                "password", IntegrationEnvironment.ADMIN_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}
