package com.calldesk.simulator;

import java.util.List;

public record ScenarioDefinition(String name, List<String> utterances, boolean bargeIn, long silenceDurationMs) {
    public ScenarioDefinition { utterances = List.copyOf(utterances); }
}
