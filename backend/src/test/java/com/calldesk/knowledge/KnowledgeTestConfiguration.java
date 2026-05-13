package com.calldesk.knowledge;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@TestConfiguration
public class KnowledgeTestConfiguration {
    @Bean Clock knowledgeClock() { return Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC); }
}
