package com.calldesk.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeGapRepository extends JpaRepository<KnowledgeGap, Long> {
    Optional<KnowledgeGap> findFirstByNormalizedQuestionAndStatus(String normalizedQuestion, KnowledgeGapStatus status);
    List<KnowledgeGap> findAllByStatusOrderByTimesAskedDescLastAskedAtDesc(KnowledgeGapStatus status);
    long countByStatus(KnowledgeGapStatus status);
}
