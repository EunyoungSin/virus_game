package com.checkpoint.conversation;

import com.checkpoint.ai.AiDialogueClient;
import com.checkpoint.ai.ChatTurn;
import com.checkpoint.conversation.dto.ConversationRequest;
import com.checkpoint.conversation.dto.ConversationResponse;
import com.checkpoint.conversation.dto.ConversationTurnResponse;
import com.checkpoint.game.Game;
import com.checkpoint.game.GameRepository;
import com.checkpoint.game.GameStatus;
import com.checkpoint.visitor.Visitor;
import com.checkpoint.visitor.VisitorRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConversationService {

    private final GameRepository gameRepository;
    private final VisitorRepository visitorRepository;
    private final ConversationRepository conversationRepository;
    private final AiDialogueClient aiDialogueClient;

    public ConversationService(
            GameRepository gameRepository,
            VisitorRepository visitorRepository,
            ConversationRepository conversationRepository,
            AiDialogueClient aiDialogueClient) {
        this.gameRepository = gameRepository;
        this.visitorRepository = visitorRepository;
        this.conversationRepository = conversationRepository;
        this.aiDialogueClient = aiDialogueClient;
    }

    public ConversationResponse ask(
            Long userId, Long gameId, Long visitorId, ConversationRequest request) {
        Game game = requireOwnedInProgressGame(userId, gameId);
        Visitor visitor = requireUndecidedVisitor(game.getId(), visitorId);
        game.touchAction(Instant.now());
        gameRepository.save(game);

        List<Conversation> past =
                conversationRepository.findByGameIdAndVisitorIdOrderByTurnNoAsc(gameId, visitorId);
        List<ChatTurn> history =
                past.stream()
                        .flatMap(
                                c ->
                                        List.of(
                                                        new ChatTurn("user", c.getQuestion()),
                                                        new ChatTurn("model", c.getAnswer()))
                                                .stream())
                        .toList();

        String systemPrompt = SystemPromptBuilder.build(visitor);
        String answer;
        try {
            answer = aiDialogueClient.generateResponse(systemPrompt, history, request.question());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답 생성에 실패했습니다", e);
        }

        int turnNo = past.size() + 1;
        Conversation conversation = new Conversation();
        conversation.setGameId(gameId);
        conversation.setVisitorId(visitorId);
        conversation.setTurnNo(turnNo);
        conversation.setTopicTag(request.topicTag());
        conversation.setQuestion(request.question());
        conversation.setAnswer(answer);
        try {
            conversationRepository.save(conversation);
        } catch (DataIntegrityViolationException duplicateTurn) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "duplicate turn, please retry", duplicateTurn);
        }

        return new ConversationResponse(answer, turnNo);
    }

    public List<ConversationTurnResponse> history(Long userId, Long gameId, Long visitorId) {
        Game game = requireOwnedGame(userId, gameId);
        Visitor visitor = requireVisitorInGame(game.getId(), visitorId);
        return conversationRepository
                .findByGameIdAndVisitorIdOrderByTurnNoAsc(gameId, visitor.getId())
                .stream()
                .map(
                        c ->
                                new ConversationTurnResponse(
                                        c.getTurnNo(), c.getQuestion(), c.getAnswer(), c.getTopicTag()))
                .toList();
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

    private Game requireOwnedInProgressGame(Long userId, Long gameId) {
        Game game = requireOwnedGame(userId, gameId);
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "game is not in progress");
        }
        return game;
    }

    private Visitor requireVisitorInGame(Long gameId, Long visitorId) {
        Visitor visitor =
                visitorRepository
                        .findById(visitorId)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "visitor not found"));
        if (!visitor.getGameId().equals(gameId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "visitor not found");
        }
        return visitor;
    }

    private Visitor requireUndecidedVisitor(Long gameId, Long visitorId) {
        Visitor visitor = requireVisitorInGame(gameId, visitorId);
        if (visitor.getDecision() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "visitor already decided");
        }
        return visitor;
    }
}
