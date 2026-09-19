package com.bienvenueblainville.assistant;

import com.anthropic.client.AnthropicClient;
import com.bienvenueblainville.common.LanguageCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeAnswerComposerTest {
    @Test
    void reportsTemplateWhenTheProviderFails() {
        AnthropicClient client = mock(AnthropicClient.class);
        when(client.messages()).thenThrow(new IllegalStateException("Provider unavailable"));
        ClaudeAnswerComposer composer = new ClaudeAnswerComposer(client, "test-model",
                new TemplateAnswerComposer());
        AnswerComposer.Composition result = composer.composeWithMetadata(
                new AssistantQuestion("cardboard", LanguageCode.en),
                List.of(new RetrievedItem(1L, LanguageCode.en, "Cardboard", "Use the blue bin.",
                        null, null, null, null, 1.0)));
        assertThat(result.provider()).isEqualTo("template");
        assertThat(result.text()).contains("Use the blue bin.");
    }
}
