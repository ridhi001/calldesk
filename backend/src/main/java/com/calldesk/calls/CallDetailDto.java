package com.calldesk.calls;

import java.util.List;

public record CallDetailDto(CallSummaryDto call, List<TurnDto> turns) {
    public CallDetailDto { turns = List.copyOf(turns); }
}
