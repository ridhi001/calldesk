package com.calldesk.knowledge;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeSeedRunner implements ApplicationRunner {
    private final KnowledgeService knowledge;
    public KnowledgeSeedRunner(KnowledgeService knowledge) { this.knowledge = knowledge; }
    @Override public void run(ApplicationArguments arguments) { knowledge.seedFromProfileIfEmpty(); }
}
