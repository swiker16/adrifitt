package com.adrifit.backend.passkey.repository;

import com.adrifit.backend.passkey.domain.PasskeyCredential;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, Long> {

    List<PasskeyCredential> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PasskeyCredential> findByCredentialId(String credentialId);

    long countByUserId(Long userId);
}
