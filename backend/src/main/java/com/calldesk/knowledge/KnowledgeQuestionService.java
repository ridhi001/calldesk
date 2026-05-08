package com.calldesk.knowledge;

import com.calldesk.calls.TurnSource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeQuestionService {
    private final KnowledgeRetriever retriever;
    private final KnowledgeService knowledgeService;
    private final KnowledgeGapService gaps;

    public KnowledgeQuestionService(KnowledgeRetriever retriever, KnowledgeService knowledgeService, KnowledgeGapService gaps) {
        this.retriever = retriever;
        this.knowledgeService = knowledgeService;
        this.gaps = gaps;
    }

    public KnowledgeQuestionContext prepareAnswer(String question, String callSid) {
        List<KnowledgeRetriever.Match> matches = retriever.search(question, 5);
        List<KnowledgeRetriever.Match> confident = matches.stream().filter(KnowledgeRetriever.Match::confident).limit(3).toList();
        if (confident.isEmpty()) {
            Double bestScore = matches.isEmpty() ? null : matches.getFirst().score();
            gaps.recordIfEligible(question, callSid, bestScore);
            return new KnowledgeQuestionContext(List.of());
        }
        List<TurnSource> sources = confident.stream().map(match -> new TurnSource(match.entry().id(), match.entry().question(), match.score())).toList();
        knowledgeService.recordUsage(sources.stream().map(TurnSource::getEntryId).toList());
        return new KnowledgeQuestionContext(sources);
    }
}
