package com.bienvenueblainville.agent;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What an administrator reads before approving. It is generated from the
 * arguments that will run, so these tests are really about one thing: the line
 * cannot say something the arguments do not.
 */
class StepSummariserTest {

    @Test
    void describesAScheduleChangeInTermsOfTheValuesThatWillBeWritten() {
        String summary = StepSummariser.describe(AgentToolName.update_collection, Map.of(
                "id", 12,
                "collectionDate", "2026-10-03",
                "sector", "north",
                "collectionType", "organic",
                "binColor", "brown"));

        assertThat(summary).isEqualTo(
                "Change collection event 12 to a organic collection for sector north on 2026-10-03 (brown bin)");
    }

    @Test
    void saysWhetherANoticeWillBeVisible() {
        Map<String, Object> input = new LinkedHashMap<>(Map.of(
                "titleFr", "Collecte reportée", "startsOn", "2026-10-01", "endsOn", "2026-10-05"));

        input.put("active", true);
        assertThat(StepSummariser.describe(AgentToolName.create_notice, input)).endsWith("(visible)");

        input.put("active", false);
        assertThat(StepSummariser.describe(AgentToolName.create_notice, input)).endsWith("(hidden)");
    }

    @Test
    void callsADeletionADeletion() {
        // Deleting a notice is the one irreversible thing the agent can
        // propose, so the line must not soften it.
        assertThat(StepSummariser.describe(AgentToolName.delete_notice, Map.of("id", 3)))
                .isEqualTo("Delete notice 3 permanently");
    }

    @Test
    void marksMissingArgumentsInsteadOfPretendingTheyAreThere() {
        // A model can omit a field. The summary showing "?" is how an
        // administrator sees that before approving, rather than after.
        assertThat(StepSummariser.describe(AgentToolName.delete_collection, Map.of()))
                .isEqualTo("Delete collection event ?");
    }
}
