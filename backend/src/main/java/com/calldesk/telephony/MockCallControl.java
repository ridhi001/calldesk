package com.calldesk.telephony;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockCallControl implements CallControl {
    private final List<Action> actions = new CopyOnWriteArrayList<>();
    @Override public void transferTo(String callSid, String phoneNumber) { actions.add(new Action("transfer", callSid, phoneNumber)); }
    @Override public void hangup(String callSid) { actions.add(new Action("hangup", callSid, "")); }
    public List<Action> actions() { return List.copyOf(actions); }
    public record Action(String type, String callSid, String phoneNumber) { }
}
