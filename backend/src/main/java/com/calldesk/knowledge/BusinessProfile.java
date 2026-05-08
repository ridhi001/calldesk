package com.calldesk.knowledge;

import java.util.List;

public record BusinessProfile(String name, String greeting, String hours, String address,
                              String handoffNumber, List<Faq> faqs) {
    public BusinessProfile {
        faqs = List.copyOf(faqs);
    }

    /** A starter FAQ. Tags are optional and help retrieval match related wording. */
    public record Faq(String question, String answer, List<String> tags) {
        public Faq {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
