package com.calldesk.simulator;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class ScenarioLoader {
    private final ResourceLoader resourceLoader;
    public ScenarioLoader(ResourceLoader resourceLoader) { this.resourceLoader = resourceLoader; }

    public ScenarioDefinition load(String name) {
        if (!name.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Invalid scenario name");
        Resource resource = resourceLoader.getResource("classpath:scenarios/" + name + ".yaml");
        try (InputStream input = resource.getInputStream()) {
            Object raw = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
            if (!(raw instanceof Map<?, ?> values)) throw new IllegalStateException("Scenario must be a YAML mapping");
            Object linesValue = values.get("utterances");
            List<String> utterances = linesValue instanceof List<?> lines ? lines.stream().map(Object::toString).toList() : List.of();
            return new ScenarioDefinition(name, utterances, Boolean.TRUE.equals(values.get("bargeIn")), number(values.get("silenceDurationMs"), 0));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Unknown scenario: " + name, exception);
        }
    }

    private static long number(Object value, long fallback) { return value instanceof Number number ? number.longValue() : fallback; }
}
