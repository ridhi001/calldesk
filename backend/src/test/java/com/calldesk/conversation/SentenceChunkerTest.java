package com.calldesk.conversation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SentenceChunkerTest {
    @Test void emitsSentencesAsSoonAsTheyEnd() {
        List<String> chunks = new ArrayList<>();
        SentenceChunker chunker = new SentenceChunker(chunks::add);
        chunker.accept("Good morning. How can I help?");
        chunker.finish();
        assertThat(chunks).containsExactly("Good morning.", "How can I help?");
    }

    @Test void emitsLongCommaClausesAndFlushesRemainder() {
        List<String> chunks = new ArrayList<>();
        SentenceChunker chunker = new SentenceChunker(chunks::add);
        chunker.accept("a".repeat(90) + ", and then this part");
        chunker.finish();
        assertThat(chunks).containsExactly("a".repeat(90) + ",", "and then this part");
    }
}
