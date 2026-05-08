package com.calldesk.config;

import com.calldesk.llm.LanguageModel;
import com.calldesk.llm.MockLanguageModel;
import com.calldesk.llm.OpenRouterLanguageModel;
import com.calldesk.knowledge.KnowledgeRetriever;
import com.calldesk.stt.CartesiaSpeechToText;
import com.calldesk.stt.MockSpeechToText;
import com.calldesk.stt.MockTranscriptScripts;
import com.calldesk.stt.SpeechToText;
import com.calldesk.telephony.CallControl;
import com.calldesk.telephony.MockCallControl;
import com.calldesk.telephony.TwilioCallControl;
import com.calldesk.tts.CartesiaTextToSpeech;
import com.calldesk.tts.MockTextToSpeech;
import com.calldesk.tts.TextToSpeech;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class ProviderConfiguration {
    @Bean
    public SpeechToText speechToText(CallDeskProperties properties, MockTranscriptScripts scripts, HttpClient client, ObjectMapper mapper) {
        return switch (properties.getProviders().getStt().toLowerCase()) {
            case "mock" -> new MockSpeechToText(scripts, properties);
            case "cartesia" -> new CartesiaSpeechToText(client, mapper, properties);
            default -> throw new IllegalArgumentException("Unsupported STT provider: " + properties.getProviders().getStt());
        };
    }

    @Bean
    public LanguageModel languageModel(CallDeskProperties properties, KnowledgeRetriever retriever,
                                       HttpClient client, ObjectMapper mapper) {
        return switch (properties.getProviders().getLlm().toLowerCase()) {
            case "mock" -> new MockLanguageModel(retriever, properties);
            case "openrouter" -> new OpenRouterLanguageModel(client, mapper, properties);
            default -> throw new IllegalArgumentException("Unsupported LLM provider: " + properties.getProviders().getLlm());
        };
    }

    @Bean
    public TextToSpeech textToSpeech(CallDeskProperties properties, HttpClient client, ObjectMapper mapper) {
        return switch (properties.getProviders().getTts().toLowerCase()) {
            case "mock" -> new MockTextToSpeech(properties);
            case "cartesia" -> new CartesiaTextToSpeech(client, mapper, properties);
            default -> throw new IllegalArgumentException("Unsupported TTS provider: " + properties.getProviders().getTts());
        };
    }

    @Bean
    public CallControl callControl(CallDeskProperties properties, HttpClient client) {
        return properties.getTwilio().getAccountSid().isBlank() || properties.getTwilio().getAuthToken().isBlank()
                ? new MockCallControl() : new TwilioCallControl(client, properties);
    }
}
