package com.calldesk.llm;

import com.calldesk.config.CallDeskProperties;
import com.calldesk.conversation.CancellationToken;
import com.calldesk.knowledge.KnowledgeQuestionClassifier;
import com.calldesk.knowledge.KnowledgeRetriever;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MockLanguageModel implements LanguageModel {
    private final KnowledgeRetriever retriever;
    private final long tokenDelayMs;

    public MockLanguageModel(KnowledgeRetriever retriever, CallDeskProperties properties) {
        this.retriever = retriever;
        this.tokenDelayMs = properties.getMock().getLlmTokenDelayMs();
    }

    @Override public void stream(List<ChatMessage> messages, TokenListener listener, CancellationToken cancellationToken) {
        String callerText = messages.stream().filter(message -> message.role().equals("user")).reduce((a, b) -> b).map(ChatMessage::content).orElse("");
        String normalized = callerText.toLowerCase(Locale.ROOT);
        String answer;
        String plain = KnowledgeQuestionClassifier.normalize(callerText);
        if (asksForHuman(normalized)) {
            answer = "I’ll connect you with our receptionist now. [HANDOFF]";
        } else if (CLOSINGS.contains(plain)) {
            answer = "Thanks for calling. Have a great day!";
        } else if (plain.startsWith("thank")) {
            answer = "You're welcome. Is there anything else I can help with?";
        } else if (ACKNOWLEDGEMENTS.contains(plain)) {
            answer = "Great. Is there anything else I can help with?";
        } else {
            List<KnowledgeRetriever.Match> matches = retriever.search(callerText, 1);
            boolean confident = !matches.isEmpty() && matches.getFirst().confident();
            if (confident) answer = matches.getFirst().entry().answer();
            // A statement such as "Tomorrow afternoon works" answers the agent; it isn't a question to look up.
            else if (!KnowledgeQuestionClassifier.isKnowledgeQuestion(callerText)) answer = "Got it, I've noted that. Is there anything else I can help with?";
            else answer = "I'm not sure about that one. I can take a message or connect you with our receptionist.";
        }
        for (String word : answer.split("(?<=\\s)")) {
            cancellationToken.throwIfCancelled();
            listener.onToken(word);
            if (tokenDelayMs > 0) {
                try { Thread.sleep(tokenDelayMs); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); cancellationToken.throwIfCancelled(); }
            }
        }
    }

    private static final Set<String> CLOSINGS = Set.of("goodbye", "bye", "bye bye", "thats all", "that is all", "thats all thanks",
            "no thats all", "no thank you goodbye", "okay bye", "ok bye");
    private static final Set<String> ACKNOWLEDGEMENTS = Set.of("okay", "ok", "yes", "yes please", "sure", "sounds good",
            "perfect", "great", "alright", "all right");

    private static boolean asksForHuman(String text) {
        return text.contains("human") || text.contains("receptionist") || text.contains("talk to a person") || text.contains("speak to someone");
    }
}
