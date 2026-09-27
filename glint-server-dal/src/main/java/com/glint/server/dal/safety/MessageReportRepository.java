package com.glint.server.dal.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageReportRepository extends JpaRepository<MessageReportEntity, String> {

    List<MessageReportEntity> findByMessageId(String messageId);
}
