package com.calldesk.knowledge;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeService {
    private final KnowledgeEntryRepository repository;
    private final KnowledgeRetriever retriever;
    private final BusinessProfileLoader profileLoader;
    private final Clock clock;

    public KnowledgeService(KnowledgeEntryRepository repository, KnowledgeRetriever retriever,
                            BusinessProfileLoader profileLoader, Clock clock) {
        this.repository = repository;
        this.retriever = retriever;
        this.profileLoader = profileLoader;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntryDto> list(String query) {
        List<KnowledgeEntry> entries = repository.findAllByOrderByUpdatedAtDesc();
        if (query == null || query.isBlank()) return entries.stream().map(KnowledgeEntryDto::from).toList();
        String needle = query.toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> contains(entry.getQuestion(), needle) || contains(entry.getAnswer(), needle)
                || entry.getTags().stream().anyMatch(tag -> contains(tag, needle)))
                .map(KnowledgeEntryDto::from).toList();
    }

    @Transactional
    public KnowledgeEntryDto create(KnowledgeEntryRequest request) {
        KnowledgeEntry entry = new KnowledgeEntry(request.question().trim(), request.answer().trim(), request.tags(), clock.instant());
        KnowledgeEntry saved = repository.saveAndFlush(entry);
        retriever.rebuildIndex();
        return KnowledgeEntryDto.from(saved);
    }

    @Transactional
    public KnowledgeEntryDto update(long id, KnowledgeEntryRequest request) {
        KnowledgeEntry entry = requireEntry(id);
        entry.update(request.question().trim(), request.answer().trim(), request.tags(), clock.instant());
        KnowledgeEntry saved = repository.saveAndFlush(entry);
        retriever.rebuildIndex();
        return KnowledgeEntryDto.from(saved);
    }

    @Transactional
    public void delete(long id) {
        KnowledgeEntry entry = requireEntry(id);
        repository.delete(entry);
        repository.flush();
        retriever.rebuildIndex();
    }

    @Transactional(readOnly = true)
    public KnowledgeEntryDto get(long id) { return KnowledgeEntryDto.from(requireEntry(id)); }

    @Transactional(readOnly = true)
    public KnowledgeSearchDto search(String query) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("q must not be blank");
        List<KnowledgeRetriever.Match> matches = retriever.search(query, 5);
        List<KnowledgeMatchDto> results = matches.stream().map(match -> new KnowledgeMatchDto(match.entry(), match.score(), match.confident())).toList();
        return new KnowledgeSearchDto(query, matches.stream().anyMatch(KnowledgeRetriever.Match::confident), results);
    }

    @Transactional
    public void recordUsage(List<Long> ids) {
        if (ids.isEmpty()) return;
        Instant now = clock.instant();
        ids.stream().distinct().forEach(id -> repository.incrementUsage(id, now));
        repository.flush();
        retriever.rebuildIndex();
    }

    @Transactional
    public int seedFromProfileIfEmpty() {
        if (repository.count() > 0) return 0;
        List<BusinessProfile.Faq> faqs = profileLoader.getProfile().faqs();
        Instant now = clock.instant();
        List<KnowledgeEntry> seeded = faqs.stream()
                .map(faq -> new KnowledgeEntry(faq.question(), faq.answer(), faq.tags(), now)).toList();
        if (!seeded.isEmpty()) repository.saveAllAndFlush(seeded);
        retriever.rebuildIndex();
        return seeded.size();
    }

    private KnowledgeEntry requireEntry(long id) {
        return repository.findById(id).orElseThrow(() -> new KnowledgeNotFoundException("Knowledge entry not found: " + id));
    }

    private static boolean contains(String value, String needle) { return value != null && value.toLowerCase(Locale.ROOT).contains(needle); }
}
