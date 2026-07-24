package com.checkpoint.visitor;

import com.checkpoint.game.Game;
import com.checkpoint.game.GameRepository;
import com.checkpoint.game.GameStatus;
import com.checkpoint.visitor.dto.TestKitResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TestKitService {

    private final GameRepository gameRepository;
    private final VisitorRepository visitorRepository;

    public TestKitService(GameRepository gameRepository, VisitorRepository visitorRepository) {
        this.gameRepository = gameRepository;
        this.visitorRepository = visitorRepository;
    }

    @Transactional
    public TestKitResponse use(Long userId, Long gameId, Long visitorId) {
        Game game = requireOwnedInProgressGame(userId, gameId);
        Visitor visitor = requireUndecidedVisitor(gameId, visitorId);

        int remaining = game.getTestKitsRemaining();
        if (remaining <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no test kits remaining");
        }

        Map<String, Object> resources = new HashMap<>(game.getResourcesLeft());
        resources.put("testKit", remaining - 1);
        game.setResourcesLeft(resources);
        game.touchAction(Instant.now());
        gameRepository.save(game);

        return new TestKitResponse(visitor.getId(), visitor.isInfected(), remaining - 1);
    }

    private Game requireOwnedInProgressGame(Long userId, Long gameId) {
        Game game =
                gameRepository
                        .findById(gameId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "game not found"));
        if (!game.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "game not found");
        }
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "game is not in progress");
        }
        return game;
    }

    private Visitor requireUndecidedVisitor(Long gameId, Long visitorId) {
        Visitor visitor =
                visitorRepository
                        .findById(visitorId)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "visitor not found"));
        if (!visitor.getGameId().equals(gameId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "visitor not found");
        }
        if (visitor.getDecision() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "visitor already decided");
        }
        return visitor;
    }
}
