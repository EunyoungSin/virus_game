package com.checkpoint.conversation;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByGameIdAndVisitorIdOrderByTurnNoAsc(Long gameId, Long visitorId);

    List<Conversation> findByGameIdOrderByVisitorIdAscTurnNoAsc(Long gameId);

    void deleteByGameIdAndCreatedAtAfter(Long gameId, Instant createdAt);

    void deleteByGameId(Long gameId);
}
