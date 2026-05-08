package com.calldesk.telephony;

public interface TwilioMediaSender {
    void sendMedia(byte[] mulaw);
    void sendMark(String name);
    void clear();
    void close();
}
