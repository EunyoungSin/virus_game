package com.checkpoint.game;

import com.checkpoint.conversation.Conversation;
import com.checkpoint.conversation.ConversationRepository;
import com.checkpoint.conversation.TopicTag;
import com.checkpoint.game.dto.GameSummaryResponse;
import com.checkpoint.game.dto.SaveSlotResponse;
import com.checkpoint.visitor.Decision;
import com.checkpoint.visitor.Visitor;
import com.checkpoint.visitor.VisitorRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// 저장 슬롯은 게임이 아니라 유저 전역 자원(최대 5개)이다. 슬롯마다 완전한 스냅샷(판정/대화 포함)을
// 독립적으로 보관하며, 불러오기는 타임스탬프 비교가 아니라 "전부 초기화 후 스냅샷만 재적용"하는
// 방식으로 복원한다 — 슬롯을 어떤 순서로 선택해도 항상 정확히 같은 결과가 나오도록 하기 위함이다.
@Service
public class GameSaveService {

    private static final int MAX_SLOTS = 5;

    private final GameRepository gameRepository;
    private final GameSaveRepository gameSaveRepository;
    private final VisitorRepository visitorRepository;
    private final ConversationRepository conversationRepository;
    private final GameService gameService;

    public GameSaveService(
            GameRepository gameRepository,
            GameSaveRepository gameSaveRepository,
            VisitorRepository visitorRepository,
            ConversationRepository conversationRepository,
            GameService gameService) {
        this.gameRepository = gameRepository;
        this.gameSaveRepository = gameSaveRepository;
        this.visitorRepository = visitorRepository;
        this.conversationRepository = conversationRepository;
        this.gameService = gameService;
    }

    public List<SaveSlotResponse> listSlots(Long userId) {
        Map<Integer, GameSave> bySlot =
                gameSaveRepository.findByUserId(userId).stream()
                        .collect(Collectors.toMap(GameSave::getSlotNo, s -> s));
        List<SaveSlotResponse> result = new ArrayList<>();
        for (int slotNo = 1; slotNo <= MAX_SLOTS; slotNo++) {
            GameSave slot = bySlot.get(slotNo);
            if (slot == null) {
                result.add(SaveSlotResponse.empty(slotNo));
                continue;
            }
            Game game = gameRepository.findById(slot.getGameId()).orElse(null);
            GameStatus status = game != null ? game.getStatus() : null;
            boolean finished = game != null && game.getStatus() == GameStatus.FINISHED;
            result.add(
                    new SaveSlotResponse(
                            slotNo,
                            true,
                            slot.getGameId(),
                            slot.getCurrentDay(),
                            slot.getTrustScore(),
                            slot.getSavedAt(),
                            status,
                            finished ? game.getEndingType() : null,
                            finished ? game.getEndingReason() : null));
        }
        return result;
    }

    @Transactional
    public GameSummaryResponse save(Long userId, Long gameId, int slotNo) {
        Game game = requireOwnedGame(userId, gameId);
        List<Visitor> visitors = visitorRepository.findByGameId(gameId);
        List<Conversation> conversations =
                conversationRepository.findByGameIdOrderByVisitorIdAscTurnNoAsc(gameId);

        GameSave slot = gameSaveRepository.findByUserIdAndSlotNo(userId, slotNo).orElseGet(GameSave::new);
        Instant now = Instant.now();
        slot.setUserId(userId);
        slot.setSlotNo(slotNo);
        slot.setGameId(gameId);
        slot.setSavedAt(now);
        slot.setCurrentDay(game.getCurrentDay());
        slot.setCurrentVisitorIndex(game.getCurrentVisitorIndex());
        slot.setResourcesLeft(new HashMap<>(game.getResourcesLeft()));
        slot.setTrustScore(game.getTrustScore());
        slot.setVisitorDecisionsSnapshot(buildVisitorSnapshot(visitors));
        slot.setConversationsSnapshot(buildConversationsSnapshot(conversations));
        gameSaveRepository.save(slot);

        game.touchAction(now);
        gameRepository.save(game);
        return gameService.getSummary(userId, gameId);
    }

    // 슬롯이 가리키는 게임(slot.getGameId())이 실제 복원 대상이다 — 요청 경로의 gameId와 다를 수 있다
    // (예: "사건 이어하기" 화면에서 다른 게임의 슬롯을 선택한 경우).
    @Transactional
    public GameSummaryResponse load(Long userId, int slotNo) {
        GameSave slot =
                gameSaveRepository
                        .findByUserIdAndSlotNo(userId, slotNo)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "저장된 기록 없음"));

        Long targetGameId = slot.getGameId();
        Game game =
                gameRepository
                        .findById(targetGameId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "game not found"));
        if (!game.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "game not found");
        }

        game.setCurrentDay(slot.getCurrentDay());
        game.setCurrentVisitorIndex(slot.getCurrentVisitorIndex());
        game.setResourcesLeft(new HashMap<>(slot.getResourcesLeft()));
        game.setTrustScore(slot.getTrustScore());
        game.setStatus(GameStatus.IN_PROGRESS);
        game.setEndingType(null);
        game.setEndingReason(null);
        game.setFinishedAt(null);
        game.touchAction(Instant.now());
        gameRepository.save(game);

        restoreVisitors(targetGameId, slot.getVisitorDecisionsSnapshot());
        restoreConversations(targetGameId, slot.getConversationsSnapshot());
        // game_results는 절대 건드리지 않는다 — 이 게임의 과거 완료 이력은 그대로 유지된다.

        return gameService.getSummary(userId, targetGameId);
    }

    // 슬롯 하나만 비운다. 이 슬롯이 가리키던 게임(games/visitors/conversations)은 전혀 건드리지 않는다.
    @Transactional
    public void deleteSlot(Long userId, int slotNo) {
        gameSaveRepository.findByUserIdAndSlotNo(userId, slotNo).ifPresent(gameSaveRepository::delete);
    }

    private List<Map<String, Object>> buildVisitorSnapshot(List<Visitor> visitors) {
        return visitors.stream()
                .filter(v -> v.getDecision() != null)
                .map(
                        v -> {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("visitorId", v.getId());
                            entry.put("decision", v.getDecision().name());
                            entry.put("decidedAt", v.getDecidedAt().toString());
                            return entry;
                        })
                .toList();
    }

    private List<Map<String, Object>> buildConversationsSnapshot(List<Conversation> conversations) {
        return conversations.stream()
                .map(
                        c -> {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("visitorId", c.getVisitorId());
                            entry.put("turnNo", c.getTurnNo());
                            entry.put("topicTag", c.getTopicTag() != null ? c.getTopicTag().name() : null);
                            entry.put("question", c.getQuestion());
                            entry.put("answer", c.getAnswer());
                            entry.put("createdAt", c.getCreatedAt().toString());
                            return entry;
                        })
                .toList();
    }

    private void restoreVisitors(Long gameId, List<Map<String, Object>> snapshot) {
        List<Visitor> visitors = visitorRepository.findByGameId(gameId);
        Map<Long, Visitor> byId = visitors.stream().collect(Collectors.toMap(Visitor::getId, v -> v));
        for (Visitor v : visitors) {
            v.setDecision(null);
            v.setDecidedAt(null);
        }
        for (Map<String, Object> entry : snapshot) {
            Long visitorId = ((Number) entry.get("visitorId")).longValue();
            Visitor v = byId.get(visitorId);
            if (v == null) {
                continue;
            }
            v.setDecision(Decision.valueOf((String) entry.get("decision")));
            v.setDecidedAt(Instant.parse((String) entry.get("decidedAt")));
        }
        visitorRepository.saveAll(visitors);
    }

    private void restoreConversations(Long gameId, List<Map<String, Object>> snapshot) {
        conversationRepository.deleteByGameId(gameId);
        // Hibernate의 기본 플러시 순서는 insert가 delete보다 먼저라, flush 없이 바로 재삽입하면
        // 아직 지워지지 않은 기존 행과 (game_id, visitor_id, turn_no) unique 제약이 충돌한다.
        conversationRepository.flush();
        List<Conversation> restored =
                snapshot.stream()
                        .map(
                                entry -> {
                                    Conversation c = new Conversation();
                                    c.setGameId(gameId);
                                    c.setVisitorId(((Number) entry.get("visitorId")).longValue());
                                    c.setTurnNo(((Number) entry.get("turnNo")).intValue());
                                    Object topicTagRaw = entry.get("topicTag");
                                    c.setTopicTag(topicTagRaw != null ? TopicTag.valueOf((String) topicTagRaw) : null);
                                    c.setQuestion((String) entry.get("question"));
                                    c.setAnswer((String) entry.get("answer"));
                                    c.setCreatedAt(Instant.parse((String) entry.get("createdAt")));
                                    return c;
                                })
                        .toList();
        conversationRepository.saveAll(restored);
    }

    private Game requireOwnedGame(Long userId, Long gameId) {
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
