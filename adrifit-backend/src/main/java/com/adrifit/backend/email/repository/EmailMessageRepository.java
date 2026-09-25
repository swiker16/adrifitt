package com.adrifit.backend.email.repository;

import com.adrifit.backend.email.domain.EmailMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

    List<EmailMessage> findTop200ByOrderByCreatedAtDesc();

    List<EmailMessage> findByClientIdOrderByCreatedAtDesc(Long clientId);

    @Modifying
    @Query("UPDATE EmailMessage e SET e.clientId = NULL WHERE e.clientId = :clientId")
    void detachClient(Long clientId);
}
