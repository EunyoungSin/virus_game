package com.checkpoint.conversation;

import com.checkpoint.conversation.dto.ConversationRequest;
import com.checkpoint.conversation.dto.ConversationResponse;
import com.checkpoint.conversation.dto.ConversationTurnResponse;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/api/games/{gameId}/visitors/{visitorId}/conversations")
    public ConversationResponse ask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId,
            @PathVariable Long visitorId,
            @Valid @RequestBody ConversationRequest request) {
        return conversationService.ask(userId, gameId, visitorId, request);
    }

    @GetMapping("/api/games/{gameId}/visitors/{visitorId}/conversations")
    public List<ConversationTurnResponse> history(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId,
            @PathVariable Long visitorId) {
        return conversationService.history(userId, gameId, visitorId);
    }
}
