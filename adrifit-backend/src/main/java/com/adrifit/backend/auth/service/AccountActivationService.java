package com.adrifit.backend.auth.service;

import com.adrifit.backend.auth.dto.AuthResponse;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecureTokens;
import com.adrifit.backend.lead.dto.LeadDtos.ActivationInfo;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account activation for clients accepted through the intake flow: the emailed link lets them
 * choose their own password (no temporary password travels by email) and signs them in.
 */
@Service
public class AccountActivationService {

    public static final Duration VALIDITY = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public AccountActivationService(UserRepository userRepository, ClientRepository clientRepository,
                                    PasswordEncoder passwordEncoder, AuthService authService) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    /** Creates (or replaces) the activation token of a user and returns it in plain text once. */
    @Transactional
    public String issue(User user) {
        String token = SecureTokens.generate();
        user.setActivationTokenHash(SecureTokens.hash(token));
        user.setActivationExpiresAt(Instant.now().plus(VALIDITY));
        userRepository.save(user);
        return token;
    }

    public boolean isPending(Long userId) {
        return userRepository.findById(userId).map(u -> u.getActivationTokenHash() != null).orElse(false);
    }

    @Transactional(readOnly = true)
    public ActivationInfo info(String token) {
        User user = valid(token);
        String firstName = clientRepository.findByUserId(user.getId()).map(c -> c.getFirstName()).orElse(user.getUsername());
        return new ActivationInfo(firstName, user.getUsername(), user.getEmail());
    }

    @Transactional
    public AuthResponse activate(String token, String password) {
        User user = valid(token);
        if (password == null || password.length() < 8) {
            throw new BusinessException("La contraseña debe tener al menos 8 caracteres");
        }
        user.setPassword(passwordEncoder.encode(password));
        user.setMustChangePassword(false);
        user.setActivationTokenHash(null);
        user.setActivationExpiresAt(null);
        userRepository.save(user);
        return authService.issueToken(user);
    }

    private User valid(String token) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("Enlace de activación no válido");
        }
        User user = userRepository.findByActivationTokenHash(SecureTokens.hash(token))
                .orElseThrow(() -> new ResourceNotFoundException("Este enlace no es válido o ya se ha usado"));
        if (user.getActivationExpiresAt() == null || user.getActivationExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Este enlace ha caducado. Pide a tu entrenador que te envíe uno nuevo.");
        }
        return user;
    }
}
