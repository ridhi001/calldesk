package com.calldesk.telephony;

public interface CallControl {
    void transferTo(String callSid, String phoneNumber);
    void hangup(String callSid);
}
