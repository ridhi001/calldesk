package com.calldesk.calls;

public record TurnAddedEventDto(long callId, TurnDto turn) { }
