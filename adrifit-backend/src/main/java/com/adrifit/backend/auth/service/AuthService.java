package com.adrifit.backend.auth.service;

import com.adrifit.backend.auth.dto.AuthResponse;
import com.adrifit.backend.auth.dto.LoginRequest;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.JwtService;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String token = jwtService.generateToken(
                user.getUsername(),
                Map.of("role", user.getRole().name(), "userId", user.getId()));

        return new AuthResponse(token, user.getId(), user.getUsername(), user.getRole());
    }
}
