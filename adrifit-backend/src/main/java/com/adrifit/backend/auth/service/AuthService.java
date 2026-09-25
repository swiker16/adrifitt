package com.adrifit.backend.auth.service;

import com.adrifit.backend.auth.dto.AuthResponse;
import com.adrifit.backend.auth.dto.ChangePasswordRequest;
import com.adrifit.backend.auth.dto.LoginRequest;
import com.adrifit.backend.auth.dto.MeResponse;
import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.JwtService;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import com.adrifit.backend.user.service.UserService;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       UserRepository userRepository,
                       UserService userService,
                       ClientRepository clientRepository,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userService = userService;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, request.password()));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String token = jwtService.generateToken(
                user.getUsername(),
                Map.of("role", user.getRole().name(), "userId", user.getId()));

        return new AuthResponse(token, user.getId(), user.getUsername(), user.getRole(), user.isMustChangePassword());
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        User user = userService.getCurrentUser();
        Client client = clientRepository.findByUserId(user.getId()).orElse(null);
        return new MeResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.isMustChangePassword(),
                client != null ? client.getId() : null,
                client != null ? client.getFirstName() : null,
                client != null ? client.getLastName() : null);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = userService.getCurrentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException("La contraseña actual no es correcta");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("La nueva contraseña debe ser distinta de la actual");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }
}
