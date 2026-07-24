package com.checkpoint.game;

import com.checkpoint.conversation.ConversationRepository;
import com.checkpoint.game.dto.EndingArchiveEntryResponse;
import com.checkpoint.game.dto.GameResultResponse;
import com.checkpoint.game.dto.GameSummaryResponse;
import com.checkpoint.visitor.Decision;
import com.checkpoint.visitor.VisitorGenerationService;
import com.checkpoint.visitor.VisitorRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GameService {

    private final GameRepository gameRepository;
    private final VisitorRepository visitorRepository;
    private final VisitorGenerationService visitorGenerationService;
    private final GameResultRepository gameResultRepository;
    private final GameSaveRepository gameSaveRepository;
    private final ConversationRepository conversationRepository;

    public GameService(
            GameRepository gameRepository,
            VisitorRepository visitorRepository,
            VisitorGenerationService visitorGenerationService,
            GameResultRepository gameResultRepository,
            GameSaveRepository gameSaveRepository,
            ConversationRepository conversationRepository) {
        this.gameRepository = gameRepository;
        this.visitorRepository = visitorRepository;
        this.visitorGenerationService = visitorGenerationService;
        this.gameResultRepository = gameResultRepository;
        this.gameSaveRepository = gameSaveRepository;
        this.conversationRepository = conversationRepository;
    }

    // 게임 생성에는 더 이상 개수 제한이 없다 — 저장 슬롯 5개가 "나중에 다시 돌아올 수 있는
    // 지점"에 대한 실질적 제약 역할을 대신한다.
    @Transactional
    public GameSummaryResponse createGame(Long userId) {
        Game game = gameRepository.save(new Game(userId));
        visitorGenerationService.generateVisitors(game.getId());
        return toSummary(game);
    }

    public GameSummaryResponse getSummary(Long userId, Long gameId) {
        return toSummary(findOwnedGame(userId, gameId));
    }

    @Transactional
    public GameSummaryResponse pause(Long userId, Long gameId) {
        Game game = findOwnedGame(userId, gameId);
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "only in-progress games can be paused");
        }
        game.setStatus(GameStatus.PAUSED);
        gameRepository.save(game);
        return toSummary(game);
    }

    // 같은 게임이 여러 번 완료될 수 있으므로(슬롯으로 되돌린 뒤 재도전) 가장 최근 결과만 반환한다.
    public GameResultResponse getResult(Long userId, Long gameId) {
        Game game = findOwnedGame(userId, gameId);
        GameResult result =
                gameResultRepository
                        .findFirstByGameIdOrderByCreatedAtDesc(game.getId())
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "result not available"));
        return new GameResultResponse(
                result.getGameId(),
                result.getEndingType(),
                result.getEndingReason(),
                result.getInfectedAdmitted(),
                result.getInnocentRejected(),
                result.getTotalProcessed(),
                result.getFinalTrustScore(),
                result.getCreatedAt());
    }

    public List<EndingArchiveEntryResponse> listEndings(Long callerId, Long targetUserId) {
        requireSameUser(callerId, targetUserId);
        return gameResultRepository.findByUserIdOrderByCreatedAtDesc(targetUserId).stream()
                .map(
                        r ->
                                new EndingArchiveEntryResponse(
                                        r.getGameId(),
                                        r.getEndingType(),
                                        r.getEndingReason(),
                                        r.getTotalProcessed(),
                                        r.getInfectedAdmitted(),
                                        r.getInnocentRejected(),
                                        r.getFinalTrustScore(),
                                        r.getCreatedAt()))
                .toList();
    }

    // 진행 중인 게임을 완전히 삭제한다. 완료된 게임은 엔딩 기록 보관소에 영구 보관되는 것이 원칙이라
    // 이 API로 지울 수 없다. 이 게임을 가리키던 저장 슬롯이 있다면(0~5개, 여러 개일 수 있음) 함께
    // 비워진다. FK 제약 때문에 자식 테이블부터 삭제한다.
    @Transactional
    public void deleteGame(Long userId, Long gameId) {
        Game game = findOwnedGame(userId, gameId);
        if (game.getStatus() == GameStatus.FINISHED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "finished games cannot be deleted, only archived");
        }
        conversationRepository.deleteByGameId(gameId);
        visitorRepository.deleteByGameId(gameId);
        gameSaveRepository.deleteByGameId(gameId);
        gameRepository.delete(game);
    }

    private Game findOwnedGame(Long userId, Long gameId) {
        Game game =
                gameRepository
                        .findById(gameId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "game not found"));
        if (!game.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "game not found");
        }
        return game;
    }

    private void requireSameUser(Long callerId, Long targetUserId) {
        if (!callerId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found");
        }
    }

    private GameSummaryResponse toSummary(Game game) {
        int processedToday =
                (int)
                        visitorRepository.countByGameIdAndDayIndexAndDecisionIsNotNull(
                                game.getId(), game.getCurrentDay());
        int infectedAdmittedSoFar =
                (int) visitorRepository.countByGameIdAndInfectedTrueAndDecision(game.getId(), Decision.ADMIT);
        return new GameSummaryResponse(
                game.getId(),
                game.getStatus(),
                game.getCurrentDay(),
                processedToday,
                game.getCurrentVisitorIndex(),
                game.getTrustScore(),
                game.getTestKitsRemaining(),
                infectedAdmittedSoFar,
                game.getCreatedAt(),
                game.getUpdatedAt());
    }
}
