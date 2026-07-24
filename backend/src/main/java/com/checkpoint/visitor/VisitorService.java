package com.checkpoint.visitor;

import com.checkpoint.game.Game;
import com.checkpoint.game.GameRepository;
import com.checkpoint.game.GameStatus;
import com.checkpoint.visitor.dto.VisitorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VisitorService {

    private final VisitorRepository visitorRepository;
    private final GameRepository gameRepository;

    public VisitorService(VisitorRepository visitorRepository, GameRepository gameRepository) {
        this.visitorRepository = visitorRepository;
        this.gameRepository = gameRepository;
    }

    public VisitorResponse nextVisitor(Long userId, Long gameId) {
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
        Visitor visitor =
                visitorRepository
                        .findFirstByGameIdAndDecisionIsNullOrderByDayIndexAscOrderInDayAsc(gameId)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no pending visitor"));
        return toResponse(visitor);
    }

    private VisitorResponse toResponse(Visitor visitor) {
        return new VisitorResponse(
                visitor.getId(),
                visitor.getDayIndex(),
                visitor.getOrderInDay(),
                visitor.getName(),
                visitor.getAge(),
                visitor.getJobClaimed(),
                visitor.getOriginCity(),
                visitor.getTravelHistory());
    }
}
