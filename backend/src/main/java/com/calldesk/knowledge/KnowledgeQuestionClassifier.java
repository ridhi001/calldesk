package com.calldesk.knowledge;

import java.util.Locale;
import java.util.Set;

public final class KnowledgeQuestionClassifier {
    private static final Set<String> SMALL_TALK = Set.of("okay", "ok", "yes", "no", "yes please", "no thanks", "thank you",
            "thanks", "thanks a lot", "thanks very much", "thanks so much", "thank you very much", "thank you so much",
            "okay thanks", "ok thanks", "goodbye", "bye", "good morning", "good afternoon", "good evening", "hello", "hi",
            "that is all", "thats all", "sounds good", "okay sounds good", "ok sounds good", "sure thank you");
    private static final String[] EXCLUDED_PHRASES = {
            "leave a message", "after the tone", "at the tone", "please leave your message", "mailbox is full", "record your message",
            "talk to a person", "speak to a person", "talk to someone", "speak to someone", "human", "receptionist",
            "operator", "transfer me", "connect me to a person", "connect me to a human"
    };

    // Speech-to-text often drops the question mark, so questions and requests are also recognised by their wording.
    private static final Set<String> QUESTION_STARTS = Set.of("what", "whats", "when", "where", "wheres", "why", "who", "which",
            "how", "hows", "do", "does", "did", "is", "are", "can", "could", "will", "would", "should", "may", "have", "has");
    private static final String[] REQUEST_PHRASES = {
            "i need", "i want", "i would like", "id like", "i'd like", "im looking for", "i am looking for", "looking for",
            "tell me", "can you", "could you", "do you", "is there", "are there", "how much", "how long", "i wanted to"
    };

    private KnowledgeQuestionClassifier() { }

    public static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", "").trim().replaceAll("\\s+", " ");
    }

    public static boolean isKnowledgeQuestion(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank() || SMALL_TALK.contains(normalized)) return false;
        for (String phrase : EXCLUDED_PHRASES) if (normalized.contains(phrase)) return false;
        String[] words = normalized.split(" ");
        if (words.length < 4) return false;
        // Statements such as "Tomorrow afternoon would work for me" answer the agent; they are not questions for the KB.
        return text.contains("?") || QUESTION_STARTS.contains(words[0]) || containsRequest(normalized);
    }

    private static boolean containsRequest(String normalized) {
        for (String phrase : REQUEST_PHRASES) if ((" " + normalized + " ").contains(" " + phrase + " ")) return true;
        return false;
    }
}
