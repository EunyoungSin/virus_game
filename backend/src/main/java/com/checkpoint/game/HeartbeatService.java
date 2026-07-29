package com.checkpoint.game;

import com.checkpoint.game.dto.GameSummaryResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HeartbeatService {

    // 하트비트가 이 시간 이상 끊겼다 재개되면 "방금 복귀"로 간주하고 유휴 시계를 리셋한다
    // (며칠 만에 다시 접속하는 정상적인 사용 패턴을 유휴 타임아웃 대상에서 제외하기 위함).
    private static final Duration HEARTBEAT_GAP_RESET = Duration.ofMinutes(2);
    // 실질 행동(대화/판정/검사키트/저장/불러오기) 없이 이 시간을 넘기면 유휴 타임아웃으로 강제 종료한다.
    private static final Duration IDLE_TIMEOUT = Duration.ofHours(1);

    private final GameRepository gameRepository;
    private final GameFinishService gameFinishService;
    private final GameService gameService;

    public HeartbeatService(
            GameRepository gameRepository, GameFinishService gameFinishService, GameService gameService) {
        this.gameRepository = gameRepository;
        this.gameFinishService = gameFinishService;
        this.gameService = gameService;
    }

    // PAUSED/FINISHED 게임은 유휴 타임아웃 대상이 아니므로 상태만 그대로 반환하고 아무것도 갱신하지 않는다.
    @Transactional
    public GameSummaryResponse heartbeat(Long userId, Long gameId) {
        Game game = findOwnedGame(userId, gameId);
        if (game.getStatus() == GameStatus.IN_PROGRESS) {
            Instant now = Instant.now();
            Instant lastHeartbeat = game.getLastHeartbeatAt();
            if (lastHeartbeat == null
                    || Duration.between(lastHeartbeat, now).compareTo(HEARTBEAT_GAP_RESET) > 0) {
                game.touchAction(now);
                gameRepository.save(game);
            } else {
                Instant lastAction = game.getLastActionAt() != null ? game.getLastActionAt() : now;
                if (Duration.between(lastAction, now).compareTo(IDLE_TIMEOUT) > 0) {
                    gameFinishService.finish(game, EndingReason.IDLE_TIMEOUT);
                    gameRepository.save(game);
                } else {
                    game.setLastHeartbeatAt(now);
                    gameRepository.save(game);
                }
            }
        }
        return gameService.getSummary(userId, gameId);
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
}
