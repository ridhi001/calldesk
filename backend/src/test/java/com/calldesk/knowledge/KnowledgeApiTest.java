package com.calldesk.knowledge;

import com.calldesk.calls.CallOutcome;
import com.calldesk.calls.CallPersistenceService;
import com.calldesk.calls.CallRecord;
import com.calldesk.calls.TurnRole;
import com.calldesk.calls.TurnSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.datasource.url=jdbc:h2:mem:knowledge-api-test;DB_CLOSE_DELAY=-1", "CORS_ALLOWED_ORIGINS=https://knowledge.example"})
@AutoConfigureMockMvc
@ActiveProfiles("sim")
class KnowledgeApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private KnowledgeGapService gaps;
    @Autowired private CallPersistenceService persistence;

    @Test void exposesKnowledgeBusinessGapTurnSourceMetricsAndCorsContracts() throws Exception {
        mvc.perform(get("/api/business").header("Origin", "https://knowledge.example"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://knowledge.example"))
                .andExpect(jsonPath("$.name").value("Brightsmile Dental"))
                .andExpect(jsonPath("$.handoffNumber").value("+916745550142"))
                .andExpect(jsonPath("$.faqs").doesNotExist());

        String createBody = objectMapper.writeValueAsString(Map.of("question", "Do you offer adult orthodontics?",
                "answer", "We can arrange an orthodontic consultation.", "tags", List.of("orthodontics", "adults")));
        JsonNode created = objectMapper.readTree(mvc.perform(post("/api/knowledge").contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.question").value("Do you offer adult orthodontics?"))
                .andExpect(jsonPath("$.tags[0]").value("orthodontics")).andExpect(jsonPath("$.timesUsed").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty()).andReturn().getResponse().getContentAsString());
        long entryId = created.path("id").asLong();

        mvc.perform(get("/api/knowledge").param("q", "ADULTS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(entryId));
        mvc.perform(get("/api/knowledge/search").param("q", "Do you offer adult orthodontics?"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.query").value("Do you offer adult orthodontics?"))
                .andExpect(jsonPath("$.confident").value(true)).andExpect(jsonPath("$.matches[0].entry.id").value(entryId))
                .andExpect(jsonPath("$.matches[0].score").isNumber()).andExpect(jsonPath("$.matches[0].confident").value(true));
        mvc.perform(get("/api/knowledge/search").param("q", " "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());

        CallRecord call = persistence.startCall("CA_KNOWLEDGE_API", "+100", "+200", Instant.parse("2026-09-26T10:00:00Z"));
        KnowledgeGapDto gap = gaps.recordIfEligible("Can you fit adult braces?", "CA_KNOWLEDGE_API", 0.7);
        mvc.perform(get("/api/knowledge/gaps"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(gap.id()))
                .andExpect(jsonPath("$[0].question").value("Can you fit adult braces?"))
                .andExpect(jsonPath("$[0].timesAsked").value(1)).andExpect(jsonPath("$[0].firstAskedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].lastCallId").value(call.getId())).andExpect(jsonPath("$[0].status").value("OPEN"));

        String resolveBody = objectMapper.writeValueAsString(Map.of("question", "Can you fit adult braces?",
                "answer", "We can schedule an orthodontic consultation.", "tags", List.of("orthodontics")));
        JsonNode resolved = objectMapper.readTree(mvc.perform(post("/api/knowledge/gaps/{id}/resolve", gap.id())
                .contentType(MediaType.APPLICATION_JSON).content(resolveBody)).andExpect(status().isOk())
                .andExpect(jsonPath("$.gap.status").value("RESOLVED")).andExpect(jsonPath("$.gap.resolvedEntryId").isNumber())
                .andExpect(jsonPath("$.entry.question").value("Can you fit adult braces?"))
                .andReturn().getResponse().getContentAsString());
        long resolvedEntryId = resolved.path("entry").path("id").asLong();

        KnowledgeGapDto dismissed = gaps.recordIfEligible("Do you provide weekend pediatric visits?", "CA_KNOWLEDGE_API", 0.4);
        mvc.perform(post("/api/knowledge/gaps/{id}/dismiss", dismissed.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISMISSED"));
        mvc.perform(get("/api/knowledge/gaps").param("status", "ALL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.status == 'RESOLVED')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.status == 'DISMISSED')]").isNotEmpty());

        mvc.perform(put("/api/knowledge/{id}", entryId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "Do you offer orthodontic visits?",
                                "answer", "We offer consultations.", "tags", List.of("orthodontics")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedAt").isNotEmpty());
        mvc.perform(delete("/api/knowledge/{id}", entryId)).andExpect(status().isNoContent());
        mvc.perform(post("/api/knowledge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\" \",\"answer\":\"answer\",\"tags\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());

        persistence.addTurn("CA_KNOWLEDGE_API", 1, TurnRole.CALLER, "How do I book an appointment?",
                Instant.parse("2026-09-26T10:01:00Z"), 30L, null, null, null, false);
        persistence.addAgentTurn("CA_KNOWLEDGE_API", 1, "I can help schedule a visit.", Instant.parse("2026-09-26T10:01:00Z"),
                30L, 40L, 50L, 60L, false, List.of(new TurnSource(resolvedEntryId, "Can you fit adult braces?", 3.2)));
        persistence.endCall("CA_KNOWLEDGE_API", CallOutcome.COMPLETED, Instant.parse("2026-09-26T10:02:00Z"));
        mvc.perform(get("/api/calls/{id}", call.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.turns[0].sources").isArray())
                .andExpect(jsonPath("$.turns[0].sources").isEmpty())
                .andExpect(jsonPath("$.turns[1].sources[0].entryId").value(resolvedEntryId))
                .andExpect(jsonPath("$.turns[1].sources[0].question").value("Can you fit adult braces?"))
                .andExpect(jsonPath("$.turns[1].sources[0].score").value(3.2));
        mvc.perform(get("/api/metrics"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.knowledge.entries").isNumber())
                .andExpect(jsonPath("$.knowledge.openGaps").value(0)).andExpect(jsonPath("$.knowledge.coverageRate").value(1.0))
                .andExpect(jsonPath("$.latencyTrend[0].callId").value(call.getId()))
                .andExpect(jsonPath("$.latencyTrend[0].startedAt").isNotEmpty())
                .andExpect(jsonPath("$.latencyTrend[0].p50TurnLatencyMs").value(60.0));
    }
}
