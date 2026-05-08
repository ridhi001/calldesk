package com.calldesk.calls;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {
    Optional<CallRecord> findByCallSid(String callSid);
    Page<CallRecord> findByOutcome(CallOutcome outcome, Pageable pageable);
    @Query("select t.turnLatencyMs from TurnRecord t where t.turnLatencyMs is not null")
    List<Long> findTurnLatencies();
}
