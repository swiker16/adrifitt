package com.adrifit.backend.user.service;

import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecurityUtils;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        String username = SecurityUtils.getCurrentUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }
}
