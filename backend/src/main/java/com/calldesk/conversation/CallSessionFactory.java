package com.calldesk.conversation;

import com.calldesk.calls.CallPersistenceService;
import com.calldesk.config.CallDeskProperties;
import com.calldesk.knowledge.BusinessProfileLoader;
import com.calldesk.knowledge.PromptBuilder;
import com.calldesk.knowledge.KnowledgeQuestionService;
import com.calldesk.llm.LanguageModel;
import com.calldesk.stt.SpeechToText;
import com.calldesk.telephony.CallControl;
import com.calldesk.telephony.TwilioMediaSender;
import com.calldesk.tts.TextToSpeech;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;

@Component
public class CallSessionFactory {
    private final CallDeskProperties properties;
    private final CallPersistenceService persistence;
    private final SpeechToText speechToText;
    private final LanguageModel languageModel;
    private final TextToSpeech textToSpeech;
    private final PromptBuilder promptBuilder;
    private final KnowledgeQuestionService knowledgeQuestions;
    private final BusinessProfileLoader profileLoader;
    private final CallControl callControl;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;

    public CallSessionFactory(CallDeskProperties properties, CallPersistenceService persistence, SpeechToText speechToText,
                              LanguageModel languageModel, TextToSpeech textToSpeech, PromptBuilder promptBuilder,
                              KnowledgeQuestionService knowledgeQuestions, BusinessProfileLoader profileLoader,
                              CallControl callControl, Clock clock,
                              ScheduledExecutorService callScheduler) {
        this.properties = properties; this.persistence = persistence; this.speechToText = speechToText;
        this.languageModel = languageModel; this.textToSpeech = textToSpeech; this.promptBuilder = promptBuilder;
        this.knowledgeQuestions = knowledgeQuestions;
        this.profileLoader = profileLoader; this.callControl = callControl; this.clock = clock; this.scheduler = callScheduler;
    }

    public CallSession start(String callSid, String from, String to, TwilioMediaSender sender) {
        persistence.startCall(callSid, from, to, Instant.now(clock));
        CallSession session = new CallSession(callSid, from, to, properties, persistence, speechToText, languageModel,
                textToSpeech, promptBuilder, knowledgeQuestions, profileLoader, callControl, sender, clock, scheduler);
        session.start();
        return session;
    }
}
