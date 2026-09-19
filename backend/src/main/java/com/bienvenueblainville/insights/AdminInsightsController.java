package com.bienvenueblainville.insights;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * What the city does not have written down.
 *
 * <p>Under {@code /api/admin/**}, so ADMIN is already required. This is the
 * reason the query stream exists: not a dashboard for its own sake, but a work
 * list of entries somebody should write.
 */
@RestController
@RequestMapping("/api/admin/insights")
public class AdminInsightsController {
    private final QueryLogStore store;

    public AdminInsightsController(QueryLogStore store) {
        this.store = store;
    }

    /**
     * The materials residents asked about and the guide could not answer,
     * ranked by how many people asked.
     */
    @GetMapping("/gaps")
    public List<GuideGap> gaps(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return store.unansweredTerms(Math.clamp(days, 1, 180), Math.clamp(limit, 1, 100));
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(defaultValue = "30") int days) {
        return store.summary(Math.clamp(days, 1, 180));
    }
}
