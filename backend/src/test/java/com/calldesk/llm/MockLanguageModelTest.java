package com.calldesk.llm;

import com.calldesk.config.CallDeskProperties;
import com.calldesk.conversation.CancellationToken;
import com.calldesk.knowledge.KnowledgeRetriever;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockLanguageModelTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Goodbye.                              | Thanks for calling. Have a great day!",
            "Thank you so much                     | You're welcome. Is there anything else I can help with?",
            "Yes please                            | Great. Is there anything else I can help with?",
            "Tomorrow afternoon would work for me. | Got it, I've noted that. Is there anything else I can help with?",
            "Do you offer braces for adults?       | I'm not sure about that one. I can take a message or connect you with our receptionist.",
    })
    void repliesSensiblyWhenTheKnowledgeBaseHasNoAnswer(String caller, String expected) {
        KnowledgeRetriever retriever = mock(KnowledgeRetriever.class);
        when(retriever.search(anyString(), anyInt())).thenReturn(List.of());
        CallDeskProperties properties = new CallDeskProperties();
        properties.getMock().setLlmTokenDelayMs(0);
        StringBuilder reply = new StringBuilder();

        new MockLanguageModel(retriever, properties).stream(List.of(new ChatMessage("user", caller)), reply::append, new CancellationToken());

        assertThat(reply.toString().trim()).isEqualTo(expected);
    }
}
