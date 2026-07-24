package com.checkpoint.game;

import com.checkpoint.visitor.Decision;
import com.checkpoint.visitor.Visitor;
import com.checkpoint.visitor.VisitorRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameFinishService {

    private static final int NORMAL_ENDING_MAX_INFECTED_ADMITTED = 2;
    private static final int INNOCENT_REJECTED_DOWNGRADE_THRESHOLD = 3;

    private final VisitorRepository visitorRepository;
    private final GameResultRepository gameResultRepository;

    public GameFinishService(VisitorRepository visitorRepository, GameResultRepository gameResultRepository) {
        this.visitorRepository = visitorRepository;
        this.gameResultRepository = gameResultRepository;
    }

    // forcedReason == null: 정상 완료(12명 전원 처리) → 통계 기반으로 엔딩 계산.
    // forcedReason != null: 조기 강제 종료(신뢰 붕괴/유휴 타임아웃) → 항상 BAD, 사유는 그대로 사용.
    @Transactional
    public EndingType finish(Game game, EndingReason forcedReason) {
        game.setStatus(GameStatus.FINISHED);
        game.setFinishedAt(Instant.now());

        List<Visitor> visitors = visitorRepository.findByGameId(game.getId());
        int infectedAdmitted =
                (int)
                        visitors.stream()
                                .filter(v -> v.isInfected() && v.getDecision() == Decision.ADMIT)
                                .count();
        int innocentRejected =
                (int)
                        visitors.stream()
                                .filter(v -> !v.isInfected() && v.getDecision() == Decision.REJECT)
                                .count();
        int totalProcessed = (int) visitors.stream().filter(v -> v.getDecision() != null).count();

        EndingType endingType;
        EndingReason endingReason;
        if (forcedReason != null) {
            endingType = EndingType.BAD;
            endingReason = forcedReason;
        } else {
            endingType = calculateEnding(infectedAdmitted, innocentRejected);
            endingReason = endingType == EndingType.BAD ? EndingReason.INFECTION_SPREAD : null;
        }
        game.setEndingType(endingType);
        game.setEndingReason(endingReason);
        // game_results는 매번 새 row로 추가된다(덮어쓰지 않음) — 같은 게임이 슬롯으로 되돌아간 뒤
        // 재도전해 여러 번 완료되면 그만큼 완료 이력이 쌓인다.
        gameResultRepository.save(
                new GameResult(
                        game.getId(),
                        endingType,
                        endingReason,
                        infectedAdmitted,
                        innocentRejected,
                        totalProcessed,
                        game.getTrustScore()));
        // 저장 슬롯은 지우지 않는다 — 완료된 게임도 슬롯을 통해 이어할 수 있어야 한다.
        return endingType;
    }

    // 기본 등급은 감염자를 몇 명 통과시켰는지로 결정(0=BEST, 1~2=NORMAL, 3+=BAD).
    // 무고한 방문자를 3명 이상 거부했다면("전원 거부" 전략 방지) 한 단계 하향한다.
    private EndingType calculateEnding(int infectedAdmitted, int innocentRejected) {
        EndingType base =
                infectedAdmitted == 0
                        ? EndingType.BEST
                        : infectedAdmitted <= NORMAL_ENDING_MAX_INFECTED_ADMITTED
                                ? EndingType.NORMAL
                                : EndingType.BAD;
        if (innocentRejected >= INNOCENT_REJECTED_DOWNGRADE_THRESHOLD) {
            return downgrade(base);
        }
        return base;
    }

    private EndingType downgrade(EndingType ending) {
        return switch (ending) {
            case BEST -> EndingType.NORMAL;
            case NORMAL, BAD -> EndingType.BAD;
        };
    }
}
