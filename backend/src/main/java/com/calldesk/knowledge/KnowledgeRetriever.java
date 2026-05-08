package com.calldesk.knowledge;

import com.calldesk.config.CallDeskProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class KnowledgeRetriever {
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "and", "are", "as", "at", "be", "by", "do", "for", "from", "how", "i", "in", "is", "it", "me", "of", "on", "or", "our", "the", "to", "we", "what", "when", "where", "with", "you", "your", "can", "could", "would", "please", "tell", "about", "does", "did", "was", "were", "this", "that",
            "my", "there", "any", "am", "if", "so", "have", "has",
            // Generic request words say nothing about the topic. Left in, "Is there a female dentist available?"
            // confidently matched "Is parking available?" on the word "available" alone.
            "offer", "provide", "get", "need", "want", "know", "available",
            // Conversational filler carries no topic but would otherwise count as a distinctive, unmatched word.
            "like", "actually", "just", "really", "also", "hi", "hello", "hey", "um", "uh", "well", "maybe", "okay", "ok",
            "wondering", "wonder", "think", "id", "im");
    // Only matches scoring close to the best one count as confident, so a passing mention ("outside opening hours" in
    // the emergency answer) is not cited as a source for an opening-hours question.
    private static final double MIN_SHARE_OF_TOP_SCORE = 0.6;
    // A confident match must also cover this share of the question's meaning, weighting each word by how distinctive it
    // is (IDF). Words the knowledge base has never seen ("female", "installments") weigh the most, so one shared word
    // can't make an unrelated entry look like an answer.
    private static final double MIN_QUERY_COVERAGE = 0.5;
    private final KnowledgeEntryRepository repository;
    private final double minScore;
    private volatile List<IndexedEntry> index = List.of();

    public KnowledgeRetriever(KnowledgeEntryRepository repository, CallDeskProperties properties) {
        this.repository = repository;
        this.minScore = properties.getKnowledge().getMinScore();
    }

    @PostConstruct
    public void initialize() { rebuildIndex(); }

    /** Builds a complete immutable snapshot, then publishes it atomically for concurrent searches. */
    public synchronized void rebuildIndex() {
        index = repository.findAll().stream().map(KnowledgeRetriever::snapshot).toList();
    }

    public List<Match> search(String query, int limit) {
        if (limit <= 0 || query == null || query.isBlank()) return List.of();
        List<IndexedEntry> documents = index;
        List<String> terms = tokens(query).stream().distinct().toList();
        if (documents.isEmpty() || terms.isEmpty()) return List.of();

        Map<String, Integer> documentFrequency = new HashMap<>();
        for (IndexedEntry document : documents) {
            for (String term : new HashSet<>(document.tokens())) documentFrequency.merge(term, 1, Integer::sum);
        }
        double averageLength = documents.stream().mapToInt(document -> document.tokens().size()).average().orElse(1.0);
        double queryWeight = terms.stream().mapToDouble(term -> idf(documents.size(), documentFrequency.getOrDefault(term, 0))).sum();
        List<Match> matches = new ArrayList<>();
        for (IndexedEntry document : documents) {
            Map<String, Integer> frequencies = new HashMap<>();
            document.tokens().forEach(term -> frequencies.merge(term, 1, Integer::sum));
            double score = 0;
            double matchedWeight = 0;
            for (String term : terms) {
                int frequency = frequencies.getOrDefault(term, 0);
                if (frequency == 0) continue;
                int df = documentFrequency.getOrDefault(term, 0);
                double idf = idf(documents.size(), df);
                matchedWeight += idf;
                double normalizedFrequency = frequency + 1.2 * (0.25 + 0.75 * document.tokens().size() / averageLength);
                score += idf * (frequency * 2.2) / normalizedFrequency;
            }
            double coverage = matchedWeight / queryWeight;
            if (score > 0) matches.add(new Match(document.entry(), score, score >= minScore && coverage >= MIN_QUERY_COVERAGE));
        }
        double topScore = matches.stream().mapToDouble(Match::score).max().orElse(0);
        return matches.stream()
                .map(match -> match.confident() && match.score() < MIN_SHARE_OF_TOP_SCORE * topScore
                        ? new Match(match.entry(), match.score(), false) : match)
                .sorted(Comparator.comparingDouble(Match::score).reversed().thenComparing(match -> match.entry().id()))
                .limit(limit).toList();
    }

    public List<Match> confidentMatches(String query, int limit) {
        return search(query, Math.max(limit, 1)).stream().filter(Match::confident).limit(limit).toList();
    }

    public double minScore() { return minScore; }

    public static List<String> tokens(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isBlank() && !STOP_WORDS.contains(token)) result.add(stem(token));
        }
        return List.copyOf(result);
    }

    private static double idf(int documents, int documentFrequency) {
        return Math.log(1 + (documents - documentFrequency + 0.5) / (documentFrequency + 0.5));
    }

    /** Light suffix stripping so "opening"/"open" and "appointments"/"appointment" match. Applied to queries and entries alike. */
    static String stem(String token) {
        if (token.length() <= 4) return token;
        if (token.endsWith("ing") && token.length() > 5) return token.substring(0, token.length() - 3);
        if (token.endsWith("ed") && token.length() > 5) return token.substring(0, token.length() - 2);
        if (token.endsWith("s") && !token.endsWith("ss")) return token.substring(0, token.length() - 1);
        return token;
    }

    private static IndexedEntry snapshot(KnowledgeEntry entry) {
        KnowledgeEntryDto dto = KnowledgeEntryDto.from(entry);
        // The question states what an entry is about, so its words count twice (a light form of BM25F field weighting).
        List<String> terms = tokens(dto.question() + " " + dto.question() + " " + dto.answer() + " " + String.join(" ", dto.tags()));
        return new IndexedEntry(dto, terms);
    }

    public record Match(KnowledgeEntryDto entry, double score, boolean confident) { }
    private record IndexedEntry(KnowledgeEntryDto entry, List<String> tokens) { }
}
