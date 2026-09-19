package com.bienvenueblainville.assistant;

import com.bienvenueblainville.assistant.dto.AssistantAnswer;
import com.bienvenueblainville.assistant.dto.AssistantRequest;
import com.bienvenueblainville.assistant.dto.AssistantSource;
import com.bienvenueblainville.assistant.dto.AssistantSource;
import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.insights.QueryEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Retrieval-augmented answering over the municipal sorting guide.
 *
 * <p>The rule this class exists to enforce: <b>no context, no answer.</b> If
 * retrieval finds nothing relevant, it returns a refusal without calling a
 * model at all. That is not caution for its own sake — telling a resident to
 * put paint in the blue bin is a real-world wrong answer with a real-world
 * consequence, and "the model was confident" is not a defence. Refusing is
 * also free and instant, which is a pleasant side effect rather than the
 * reason.
 */
@Service
public class AssistantService {
    private static final Map<LanguageCode, String> NO_ANSWER = Map.of(
            LanguageCode.fr, "Je ne trouve pas cette matière dans le guide de tri de Blainville. "
                    + "Consultez blainville.ca ou communiquez avec la Ville pour en avoir le cœur net.",
            LanguageCode.en, "I can't find that material in the Blainville sorting guide. "
                    + "Check blainville.ca or contact the city to be sure.",
            LanguageCode.zh, "在 Blainville 分类指南中找不到这种物品。"
                    + "请查阅 blainville.ca 或联系市政府确认。"
    );

    private final SortingGuideRetriever retriever;
    private final AnswerComposer composer;
    private final QueryEventPublisher queries;
    private final AnswerCache cache;

    public AssistantService(
            SortingGuideRetriever retriever,
            AnswerComposer composer,
            QueryEventPublisher queries,
            AnswerCache cache
    ) {
        this.retriever = retriever;
        this.composer = composer;
        this.queries = queries;
        this.cache = cache;
    }

    public AssistantAnswer ask(AssistantRequest request) {
        AssistantQuestion question = new AssistantQuestion(request.question(), request.language());

        // A municipal sorting guide gets the same twenty questions over and
        // over. Serving a repeat from cache skips retrieval and, with Claude
        // configured, a model call for wording that is already known.
        Optional<AssistantAnswer> cached =
                cache.find(question.text(), question.language(), composer.providerName());
        if (cached.isPresent()) {
            // Still recorded. A cache hit is a resident asking - if repeats
            // stopped being counted, the gap report would systematically
            // under-count exactly the questions residents ask most, which is
            // the opposite of what it is for.
            AssistantAnswer answer = cached.get();
            queries.record("text-cached", question.language(), question.text(),
                    retriever.searchTermsFor(question.text(), question.language()),
                    answer.grounded(),
                    answer.sources().isEmpty() ? 0d : answer.sources().get(0).score(),
                    answer.sources().stream().map(AssistantSource::name).toList());
            return answer;
        }

        List<RetrievedItem> context = retriever.retrieve(question.text(), question.language());

        // Recorded whichever way it goes, but the refusals are the point: a
        // question the guide cannot answer used to vanish, so nobody found out
        // that fourteen residents asked about aquariums last month.
        queries.record("text", question.language(), question.text(),
                retriever.searchTermsFor(question.text(), question.language()),
                !context.isEmpty(),
                context.isEmpty() ? 0d : context.get(0).score(),
                context.stream().map(RetrievedItem::name).toList());

        if (context.isEmpty()) {
            AssistantAnswer refusal = new AssistantAnswer(
                    NO_ANSWER.get(question.language()),
                    false,
                    "template",
                    List.of());
            // Refusals are cached too, and they are the cheapest win: the same
            // unlisted material gets asked about repeatedly, and every repeat
            // otherwise re-runs a full-text search to find nothing again.
            cache.store(question.text(), question.language(), composer.providerName(), refusal);
            return refusal;
        }

        AnswerComposer.Composition composition = composer.composeWithMetadata(question, context);
        AssistantAnswer answer = new AssistantAnswer(
                composition.text(),
                true,
                composition.provider(),
                context.stream().map(AssistantService::toSource).toList());

        // Keyed on the configured composer, not the one that produced this
        // answer: a Claude call that fell back to the template must not poison
        // the cache with template wording for everyone afterwards.
        cache.store(question.text(), question.language(), composer.providerName(), answer);
        return answer;
    }

    private static AssistantSource toSource(RetrievedItem item) {
        return new AssistantSource(
                item.itemId(),
                item.name(),
                item.destinationType() == null ? null : item.destinationType().name(),
                item.binColor() == null ? null : item.binColor().name(),
                item.sourceUrl(),
                Math.round(item.score() * 1000d) / 1000d);
    }
}
