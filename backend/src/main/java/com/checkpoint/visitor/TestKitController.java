package com.checkpoint.visitor;

import com.checkpoint.visitor.dto.TestKitResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestKitController {

    private final TestKitService testKitService;

    public TestKitController(TestKitService testKitService) {
        this.testKitService = testKitService;
    }

    @PostMapping("/api/games/{gameId}/visitors/{visitorId}/test-kit")
    public TestKitResponse use(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId,
            @PathVariable Long visitorId) {
        return testKitService.use(userId, gameId, visitorId);
    }
}
