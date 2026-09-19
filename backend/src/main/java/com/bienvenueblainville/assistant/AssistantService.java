package com.bienvenueblainville.assistant;

import com.bienvenueblainville.assistant.dto.AssistantAnswer;
import com.bienvenueblainville.assistant.dto.AssistantRequest;
import com.bienvenueblainville.assistant.dto.AssistantSource;
import com.bienvenueblainville.common.LanguageCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

    public AssistantService(SortingGuideRetriever retriever, AnswerComposer composer) {
        this.retriever = retriever;
        this.composer = composer;
    }

    public AssistantAnswer ask(AssistantRequest request) {
        AssistantQuestion question = new AssistantQuestion(request.question(), request.language());
        List<RetrievedItem> context = retriever.retrieve(question.text(), question.language());

        if (context.isEmpty()) {
            return new AssistantAnswer(
                    NO_ANSWER.get(question.language()),
                    false,
                    "template",
                    List.of());
        }

        AnswerComposer.Composition composition = composer.composeWithMetadata(question, context);
        return new AssistantAnswer(
                composition.text(),
                true,
                composition.provider(),
                context.stream().map(AssistantService::toSource).toList());
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
