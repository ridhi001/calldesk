package com.calldesk.knowledge;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {
    private final BusinessProfileLoader profileLoader;
    private final KnowledgeRetriever retriever;

    public PromptBuilder(BusinessProfileLoader profileLoader, KnowledgeRetriever retriever) {
        this.profileLoader = profileLoader;
        this.retriever = retriever;
    }

    public String build(String callerText) {
        BusinessProfile profile = profileLoader.getProfile();
        List<KnowledgeRetriever.Match> matches = retriever.search(callerText, 5).stream()
                .filter(KnowledgeRetriever.Match::confident).limit(3).toList();
        StringBuilder prompt = new StringBuilder("You are the phone receptionist for ").append(profile.name()).append(".\n")
                .append("Business facts: hours: ").append(profile.hours()).append("; address: ").append(profile.address()).append(".\n")
                .append("Handoff phone number: ").append(profile.handoffNumber()).append(".\n");
        if (matches.isEmpty()) {
            prompt.append("There is no verified information for the caller's current question. Say that briefly and offer to take a message or transfer them. Never invent facts.\n");
        } else {
            prompt.append("Relevant verified FAQ:\n");
            for (KnowledgeRetriever.Match match : matches) prompt.append("Q: ").append(match.entry().question()).append("\nA: ").append(match.entry().answer()).append('\n');
        }
        return prompt.append("Rules: use short spoken sentences, no markdown, ask one question at a time, and emit [HANDOFF] when a person is needed.").toString();
    }
}
