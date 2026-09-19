package com.bienvenueblainville.assistant;

import com.bienvenueblainville.common.LanguageCode;

import java.util.List;
import java.util.Map;

/**
 * The default composer: builds the answer directly from the retrieved entries,
 * with no model call.
 *
 * <p>It is not a mock. It is the honest answer to "what should this endpoint
 * do when nobody has configured an API key" — the retrieval layer already
 * found the right entry, and reading that entry back in the resident's
 * language is genuinely useful. Consequences worth having: the feature works
 * out of the box for anyone who clones the repository, costs nothing to run,
 * is deterministic enough for CI to assert on, and gives the Claude-backed
 * composer something to be compared against rather than merely replacing.
 */
public class TemplateAnswerComposer implements AnswerComposer {
    private static final Map<LanguageCode, String> LEAD = Map.of(
            LanguageCode.fr, "D'après le guide de tri de Blainville :",
            LanguageCode.en, "According to the Blainville sorting guide:",
            LanguageCode.zh, "根据 Blainville 分类指南："
    );

    private static final Map<LanguageCode, String> ALSO = Map.of(
            LanguageCode.fr, "Entrées connexes :",
            LanguageCode.en, "Related entries:",
            LanguageCode.zh, "相关条目："
    );

    @Override
    public String providerName() {
        return "template";
    }

    @Override
    public String compose(AssistantQuestion question, List<RetrievedItem> context) {
        RetrievedItem best = context.get(0);
        StringBuilder answer = new StringBuilder()
                .append(LEAD.get(question.language())).append(' ')
                .append(best.name()).append(" — ").append(best.instruction());

        if (best.location() != null && !best.location().isBlank()) {
            answer.append(' ').append(best.location());
        }

        List<RetrievedItem> others = context.subList(1, context.size());
        if (!others.isEmpty()) {
            answer.append("\n\n").append(ALSO.get(question.language())).append(' ')
                    .append(others.stream().map(RetrievedItem::name).reduce((a, b) -> a + ", " + b).orElse(""));
        }

        return answer.toString();
    }
}
