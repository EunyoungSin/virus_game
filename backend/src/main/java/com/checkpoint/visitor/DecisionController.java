package com.checkpoint.visitor;

import com.checkpoint.visitor.dto.DecisionRequest;
import com.checkpoint.visitor.dto.DecisionResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DecisionController {

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping("/api/games/{gameId}/visitors/{visitorId}/decision")
    public DecisionResponse decide(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId,
            @PathVariable Long visitorId,
            @Valid @RequestBody DecisionRequest request) {
        return decisionService.decide(userId, gameId, visitorId, request);
    }
}
