package com.adrifit.backend.message.repository;

import com.adrifit.backend.message.domain.Message;
import com.adrifit.backend.user.domain.Role;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByClientIdOrderByCreatedAtAscIdAsc(Long clientId);

    long countByClientIdAndSenderRoleAndReadAtIsNull(Long clientId, Role senderRole);

    long countBySenderRoleAndReadAtIsNull(Role senderRole);

    @Query("SELECT m FROM Message m WHERE m.id IN (SELECT MAX(m2.id) FROM Message m2 GROUP BY m2.clientId)")
    List<Message> findLastMessagePerClient();

    @Query("SELECT m.clientId, COUNT(m) FROM Message m WHERE m.senderRole = :role AND m.readAt IS NULL GROUP BY m.clientId")
    List<Object[]> countUnreadGroupedByClient(Role role);

    @Modifying
    @Query("UPDATE Message m SET m.readAt = :now WHERE m.clientId = :clientId AND m.senderRole = :senderRole AND m.readAt IS NULL")
    int markRead(Long clientId, Role senderRole, Instant now);

    void deleteByClientId(Long clientId);
}
