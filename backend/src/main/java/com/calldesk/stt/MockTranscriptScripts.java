package com.calldesk.stt;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

@Component
public class MockTranscriptScripts {
    private final ConcurrentMap<String, ConcurrentLinkedQueue<String>> scripts = new ConcurrentHashMap<>();

    public void register(String callSid, Collection<String> utterances) {
        scripts.put(callSid, new ConcurrentLinkedQueue<>(utterances));
    }

    public String next(String callSid) {
        ConcurrentLinkedQueue<String> queue = scripts.get(callSid);
        if (queue == null) return "";
        String line = queue.poll();
        if (queue.isEmpty()) scripts.remove(callSid, queue);
        return line == null ? "" : line;
    }
}
