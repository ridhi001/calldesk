package com.calldesk.calls;

public class CallNotFoundException extends RuntimeException {
    public CallNotFoundException(String message) { super(message); }
}
