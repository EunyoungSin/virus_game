package com.checkpoint.visitor;

import com.checkpoint.config.GameRulesProperties;
import com.checkpoint.game.EndingReason;
import com.checkpoint.game.EndingType;
import com.checkpoint.game.Game;
import com.checkpoint.game.GameFinishService;
import com.checkpoint.game.GameRepository;
import com.checkpoint.game.GameService;
import com.checkpoint.game.GameStatus;
import com.checkpoint.game.dto.GameSummaryResponse;
import com.checkpoint.visitor.dto.DecisionRequest;
import com.checkpoint.visitor.dto.DecisionResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DecisionService {

    private static final int INNOCENT_REJECTED_PENALTY = 10;
    private static final int TRUST_COLLAPSE_THRESHOLD = 50;

    private final GameRepository gameRepository;
    private final VisitorRepository visitorRepository;
    private final GameFinishService gameFinishService;
    private final GameService gameService;
    private final GameRulesProperties rules;

    public DecisionService(
            GameRepository gameRepository,
            VisitorRepository visitorRepository,
            GameFinishService gameFinishService,
            GameService gameService,
            GameRulesProperties rules) {
        this.gameRepository = gameRepository;
        this.visitorRepository = visitorRepository;
        this.gameFinishService = gameFinishService;
        this.gameService = gameService;
        this.rules = rules;
    }

    @Transactional
    public DecisionResponse decide(
            Long userId, Long gameId, Long visitorId, DecisionRequest request) {
        Game game = requireOwnedInProgressGame(userId, gameId);
        Visitor visitor = requireCurrentPendingVisitor(gameId, visitorId);

        Instant now = Instant.now();
        game.touchAction(now);

        boolean correct = isCorrectDecision(visitor, request.decision());
        visitor.setDecision(request.decision());
        visitor.setDecidedAt(now);
        visitorRepository.save(visitor);

        // 오탐(비감염자를 거부)만 신뢰도에 즉시 영향을 준다. 감염자를 허가하는 대가는
        // 즉시 감점이 아니라 최종 엔딩 등급으로만 반영된다("전원 거부" 필승 전략 방지).
        boolean wronglyRejectedInnocent = !correct && !visitor.isInfected();
        if (wronglyRejectedInnocent) {
            game.setTrustScore(Math.max(0, game.getTrustScore() - INNOCENT_REJECTED_PENALTY));
        }
        game.setCurrentVisitorIndex(game.getCurrentVisitorIndex() + 1);

        EndingType endingType;
        if (game.getTrustScore() <= TRUST_COLLAPSE_THRESHOLD) {
            // 신뢰 붕괴: 남은 일차와 무관하게 즉시 BAD로 종료.
            endingType = gameFinishService.finish(game, EndingReason.TRUST_COLLAPSE);
        } else {
            boolean dayComplete =
                    visitorRepository.countByGameIdAndDayIndexAndDecisionIsNotNull(gameId, game.getCurrentDay())
                            >= rules.getVisitorsPerDay();
            if (dayComplete && game.getCurrentDay() >= rules.getDays()) {
                endingType = gameFinishService.finish(game, null);
            } else {
                if (dayComplete) {
                    game.setCurrentDay(game.getCurrentDay() + 1);
                }
                endingType = null;
            }
        }
        gameRepository.save(game);

        GameSummaryResponse summary = gameService.getSummary(userId, gameId);
        return new DecisionResponse(visitor.getId(), request.decision(), correct, summary, endingType);
    }

    private boolean isCorrectDecision(Visitor visitor, Decision decision) {
        return visitor.isInfected() ? decision == Decision.REJECT : decision == Decision.ADMIT;
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

    private Visitor requireCurrentPendingVisitor(Long gameId, Long visitorId) {
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
        Visitor pending =
                visitorRepository
                        .findFirstByGameIdAndDecisionIsNullOrderByDayIndexAscOrderInDayAsc(gameId)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no pending visitor"));
        if (!pending.getId().equals(visitor.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "visitor is not the current pending visitor");
        }
        return visitor;
    }
}
