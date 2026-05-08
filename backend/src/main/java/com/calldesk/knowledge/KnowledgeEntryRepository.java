package com.calldesk.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, Long> {
    List<KnowledgeEntry> findAllByOrderByUpdatedAtDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update KnowledgeEntry e set e.timesUsed = e.timesUsed + 1, e.lastUsedAt = :usedAt where e.id = :id")
    int incrementUsage(@Param("id") Long id, @Param("usedAt") Instant usedAt);
}
