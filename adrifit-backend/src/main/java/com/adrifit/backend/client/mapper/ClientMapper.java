package com.adrifit.backend.client.mapper;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.dto.ClientResponse;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

    private final UserRepository userRepository;

    public ClientMapper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public ClientResponse toResponse(Client client) {
        User user = userRepository.findById(client.getUserId()).orElse(null);
        return new ClientResponse(
                client.getId(),
                client.getUserId(),
                user != null ? user.getUsername() : null,
                user != null ? user.getEmail() : null,
                client.getFirstName(),
                client.getLastName(),
                client.getPhone(),
                client.getBirthDate(),
                client.getObjective(),
                client.getNotes(),
                client.getTrainerId(),
                client.getPhotoBase64(),
                client.getCreatedAt()
        );
    }
}
