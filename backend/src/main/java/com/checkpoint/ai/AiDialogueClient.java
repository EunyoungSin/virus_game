package com.checkpoint.ai;

import java.util.List;

public interface AiDialogueClient {

    String generateResponse(String systemPrompt, List<ChatTurn> history, String userQuestion);
}
