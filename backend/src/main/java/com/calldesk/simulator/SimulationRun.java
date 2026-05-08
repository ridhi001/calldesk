package com.calldesk.simulator;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

public final class SimulationRun {
    private final String callSid;
    private final List<byte[]> agentAudio = new CopyOnWriteArrayList<>();
    private final List<String> marks = new CopyOnWriteArrayList<>();
    private final AtomicLong playbackReadyNanos = new AtomicLong();
    private final Set<String> pendingMarks = ConcurrentHashMap.newKeySet();
    private volatile long lastMediaNanos;
    private volatile long lastMarkNanos;
    private volatile boolean connected;

    public SimulationRun(String callSid) { this.callSid = callSid; }
    public String callSid() { return callSid; }
    public List<byte[]> agentAudio() { return List.copyOf(agentAudio); }
    public List<String> marks() { return List.copyOf(marks); }
    public long playbackReadyNanos() { return playbackReadyNanos.get(); }
    public long lastMediaNanos() { return lastMediaNanos; }
    public long lastMarkNanos() { return lastMarkNanos; }
    public boolean isConnected() { return connected; }
    void setConnected(boolean connected) { this.connected = connected; }
    void recordAudio(byte[] bytes) {
        agentAudio.add(bytes.clone());
        lastMediaNanos = System.nanoTime();
        long now = System.nanoTime();
        long start = Math.max(now, playbackReadyNanos.get());
        playbackReadyNanos.set(start + TimeUnitNanos.fromAudioBytes(bytes.length));
    }
    void recordMark(String name) { marks.add(name); pendingMarks.add(name); lastMarkNanos = System.nanoTime(); }
    boolean consumeMark(String name) { return pendingMarks.remove(name); }
    List<String> clearPendingMarks() { List<String> copy = List.copyOf(pendingMarks); pendingMarks.clear(); return copy; }
    void clearPlayback() { playbackReadyNanos.set(System.nanoTime()); }

    private static final class TimeUnitNanos {
        private static long fromAudioBytes(int count) { return count * 125_000L; }
    }
}
