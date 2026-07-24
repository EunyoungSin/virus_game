package com.checkpoint.game;

import com.checkpoint.game.dto.GameSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    public HeartbeatController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @PostMapping("/api/games/{gameId}/heartbeat")
    public GameSummaryResponse heartbeat(
            @AuthenticationPrincipal Long userId, @PathVariable Long gameId) {
        return heartbeatService.heartbeat(userId, gameId);
    }
}
