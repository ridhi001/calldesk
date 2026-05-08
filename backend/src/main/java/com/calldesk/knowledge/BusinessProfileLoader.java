package com.calldesk.knowledge;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BusinessProfileLoader {
    private final ResourceLoader resourceLoader;
    private final com.calldesk.config.CallDeskProperties properties;
    private volatile BusinessProfile profile;

    public BusinessProfileLoader(ResourceLoader resourceLoader, com.calldesk.config.CallDeskProperties properties) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        profile = load();
    }

    public BusinessProfile getProfile() {
        BusinessProfile current = profile;
        if (current == null) {
            synchronized (this) {
                if (profile == null) profile = load();
                current = profile;
            }
        }
        return current;
    }

    private BusinessProfile load() {
        Resource resource = resourceLoader.getResource(properties.getBusinessProfile());
        try (InputStream input = resource.getInputStream()) {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
            if (!(loaded instanceof Map<?, ?> map)) throw new IllegalStateException("Business profile must be a YAML mapping");
            List<BusinessProfile.Faq> faqs = new ArrayList<>();
            Object rawFaqs = map.get("faqs");
            if (rawFaqs instanceof List<?> items) {
                for (Object item : items) {
                    if (!(item instanceof Map<?, ?> faq)) continue;
                    faqs.add(new BusinessProfile.Faq(string(faq, "question"), string(faq, "answer"), strings(faq, "tags")));
                }
            }
            return new BusinessProfile(string(map, "name"), string(map, "greeting"), string(map, "hours"),
                    string(map, "address"), string(map, "handoffNumber"), faqs);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load business profile: " + properties.getBusinessProfile(), exception);
        }
    }

    private static List<String> strings(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof List<?> values)) return List.of();
        return values.stream().filter(value -> value != null && !value.toString().isBlank())
                .map(value -> value.toString().trim().toLowerCase(java.util.Locale.ROOT)).toList();
    }

    private static String string(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }
}
