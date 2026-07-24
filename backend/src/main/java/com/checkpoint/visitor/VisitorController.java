package com.checkpoint.visitor;

import com.checkpoint.visitor.dto.VisitorResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VisitorController {

    private final VisitorService visitorService;

    public VisitorController(VisitorService visitorService) {
        this.visitorService = visitorService;
    }

    @GetMapping("/api/games/{gameId}/next-visitor")
    public VisitorResponse nextVisitor(
            @AuthenticationPrincipal Long userId, @PathVariable Long gameId) {
        return visitorService.nextVisitor(userId, gameId);
    }
}
