package com.calldesk.conversation;

import java.util.Objects;
import java.util.function.Consumer;

public final class SentenceChunker {
    private static final int LONG_COMMA_CLAUSE = 90;
    private final StringBuilder pending = new StringBuilder();
    private final Consumer<String> sentenceConsumer;

    public SentenceChunker(Consumer<String> sentenceConsumer) { this.sentenceConsumer = Objects.requireNonNull(sentenceConsumer); }

    public void accept(String token) {
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            pending.append(character);
            if (character == '.' || character == '?' || character == '!'
                    || (character == ',' && pending.length() >= LONG_COMMA_CLAUSE)) emit();
        }
    }

    public void finish() { emit(); }

    private void emit() {
        String sentence = pending.toString().trim();
        pending.setLength(0);
        if (!sentence.isEmpty()) sentenceConsumer.accept(sentence);
    }
}
