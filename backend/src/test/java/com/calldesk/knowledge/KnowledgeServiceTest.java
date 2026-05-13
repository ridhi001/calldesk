package com.calldesk.knowledge;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({KnowledgeService.class, KnowledgeRetriever.class, BusinessProfileLoader.class, KnowledgeTestConfiguration.class})
class KnowledgeServiceTest {
    @Autowired private KnowledgeService knowledge;
    @Autowired private KnowledgeRetriever retriever;
    @Autowired private KnowledgeEntryRepository entries;

    @Test void createUpdateDeleteRebuildsSearchIndexImmediately() {
        KnowledgeEntryDto created = knowledge.create(new KnowledgeEntryRequest(
                "Can I book a pediatric visit?", "Yes, call us to schedule a pediatric visit.", List.of("children", "appointments")));
        assertThat(created.id()).isNotNull();
        assertThat(retriever.search("pediatric visit", 1)).hasSize(1);
        assertThat(knowledge.list("CHILDREN")).extracting(KnowledgeEntryDto::id).containsExactly(created.id());

        KnowledgeEntryDto updated = knowledge.update(created.id(), new KnowledgeEntryRequest(
                "Do you treat children?", "Yes, our dentists see children.", List.of("orthodontics")));
        assertThat(updated.updatedAt()).isEqualTo(created.updatedAt());
        assertThat(retriever.search("pediatric visit", 1)).isEmpty();
        assertThat(retriever.search("dentists see children", 1).getFirst().entry().id()).isEqualTo(created.id());

        knowledge.delete(created.id());
        assertThat(retriever.search("dentists see children", 1)).isEmpty();
        assertThat(entries.existsById(created.id())).isFalse();
    }

    @Test void usageIsCountedWithoutChangingTheEditTime() {
        KnowledgeEntryDto created = knowledge.create(new KnowledgeEntryRequest("Do you open late?", "Until 7 pm on weekdays.", List.of()));
        knowledge.recordUsage(List.of(created.id()));
        KnowledgeEntry used = entries.findById(created.id()).orElseThrow();
        assertThat(used.getTimesUsed()).isEqualTo(1);
        assertThat(used.getLastUsedAt()).isNotNull();
        assertThat(used.getUpdatedAt()).as("updatedAt means last edited, not last used").isEqualTo(created.updatedAt());
    }

    @Test void seedsYamlFaqsOnlyWhenTheTableIsEmpty() {
        assertThat(knowledge.seedFromProfileIfEmpty()).isEqualTo(12);
        assertThat(entries.count()).isEqualTo(12);
        assertThat(knowledge.seedFromProfileIfEmpty()).isZero();
        assertThat(entries.count()).isEqualTo(12);

        entries.deleteAll();
        knowledge.create(new KnowledgeEntryRequest("Custom question", "Custom answer", List.of()));
        assertThat(knowledge.seedFromProfileIfEmpty()).isZero();
        assertThat(entries.count()).isEqualTo(1);
    }

    @Test void requestBeanValidationRejectsBlankAndOversizedFields() {
        Validator validator;
        try (var factory = Validation.buildDefaultValidatorFactory()) { validator = factory.getValidator(); }
        KnowledgeEntryRequest blank = new KnowledgeEntryRequest(" ", " ", List.of());
        assertThat(validator.validate(blank)).hasSize(2);
        KnowledgeEntryRequest tooLong = new KnowledgeEntryRequest("q".repeat(301), "a".repeat(2001), List.of());
        assertThat(validator.validate(tooLong)).hasSize(2);
    }
}
