package com.checkpoint.game;

import com.checkpoint.game.dto.EndingArchiveEntryResponse;
import com.checkpoint.game.dto.GameResultResponse;
import com.checkpoint.game.dto.GameSummaryResponse;
import com.checkpoint.game.dto.SaveSlotRequest;
import com.checkpoint.game.dto.SaveSlotResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GameController {

    private final GameService gameService;
    private final GameSaveService gameSaveService;

    public GameController(GameService gameService, GameSaveService gameSaveService) {
        this.gameService = gameService;
        this.gameSaveService = gameSaveService;
    }

    @PostMapping("/api/games")
    public GameSummaryResponse create(@AuthenticationPrincipal Long userId) {
        return gameService.createGame(userId);
    }

    @GetMapping("/api/users/{userId}/endings")
    public List<EndingArchiveEntryResponse> endings(
            @AuthenticationPrincipal Long callerId, @PathVariable Long userId) {
        return gameService.listEndings(callerId, userId);
    }

    @DeleteMapping("/api/games/{gameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long gameId) {
        gameService.deleteGame(userId, gameId);
    }

    @GetMapping("/api/games/{gameId}/summary")
    public GameSummaryResponse summary(
            @AuthenticationPrincipal Long userId, @PathVariable Long gameId) {
        return gameService.getSummary(userId, gameId);
    }

    @PatchMapping("/api/games/{gameId}/pause")
    public GameSummaryResponse pause(
            @AuthenticationPrincipal Long userId, @PathVariable Long gameId) {
        return gameService.pause(userId, gameId);
    }

    @GetMapping("/api/games/{gameId}/result")
    public GameResultResponse result(
            @AuthenticationPrincipal Long userId, @PathVariable Long gameId) {
        return gameService.getResult(userId, gameId);
    }

    // 저장 슬롯(최대 5개)은 게임이 아니라 유저 전역 자원이라 "사건 이어하기" 화면 자체가 이 목록이다.
    @GetMapping("/api/users/{userId}/saves")
    public List<SaveSlotResponse> saves(
            @AuthenticationPrincipal Long callerId, @PathVariable Long userId) {
        return gameSaveService.listSlots(userId);
    }

    @DeleteMapping("/api/users/{userId}/saves/{slotNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(
            @AuthenticationPrincipal Long callerId, @PathVariable Long userId, @PathVariable int slotNo) {
        gameSaveService.deleteSlot(userId, slotNo);
    }

    @PostMapping("/api/games/{gameId}/save")
    public GameSummaryResponse save(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId,
            @Valid @RequestBody SaveSlotRequest request) {
        return gameSaveService.save(userId, gameId, request.slotNo());
    }

    // 실제 복원 대상은 슬롯이 가리키는 게임이다 — 경로의 gameId와 다를 수 있다(슬롯 조회로
    // 이미 알고 있는 값이라 프론트는 보통 일치시키지만, 서버는 슬롯의 값만 신뢰한다).
    @PostMapping("/api/games/{gameId}/load")
    public GameSummaryResponse load(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId,
            @Valid @RequestBody SaveSlotRequest request) {
        return gameSaveService.load(userId, request.slotNo());
    }
}
