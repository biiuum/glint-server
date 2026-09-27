package com.glint.server.dal.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {
    Optional<ChatSessionEntity> findFirstByMatchEventId(String matchEventId);
}
