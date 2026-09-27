package com.glint.server.dal.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnonymousSessionRepository extends JpaRepository<AnonymousSessionEntity, String> {
    Optional<AnonymousSessionEntity> findByTokenDigest(String tokenDigest);
}
