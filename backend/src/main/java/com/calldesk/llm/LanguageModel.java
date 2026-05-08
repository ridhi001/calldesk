package com.calldesk.llm;

import com.calldesk.conversation.CancellationToken;
import java.util.List;

public interface LanguageModel {
    void stream(List<ChatMessage> messages, TokenListener listener, CancellationToken cancellationToken);
}
