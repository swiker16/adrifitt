package com.adrifit.backend.client.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.dto.ClientCreatedResponse;
import com.adrifit.backend.client.dto.ClientResponse;
import com.adrifit.backend.client.dto.CreateClientRequest;
import com.adrifit.backend.client.dto.UpdateClientRequest;
import com.adrifit.backend.client.mapper.ClientMapper;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.user.domain.Role;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClientMapper clientMapper;

    public ClientService(ClientRepository clientRepository,
                         UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         ClientMapper clientMapper) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clientMapper = clientMapper;
    }

    @Transactional
    public ClientCreatedResponse create(CreateClientRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email already in use");
        }

        String username = generateUsername(request.firstName(), request.lastName());
        String temporaryPassword = generatePassword();

        User user = userRepository.save(User.builder()
                .username(username)
                .email(request.email())
                .password(passwordEncoder.encode(temporaryPassword))
                .role(Role.CLIENT)
                .build());

        Client client = clientRepository.save(Client.builder()
                .userId(user.getId())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .birthDate(request.birthDate())
                .objective(request.objective())
                .notes(request.notes())
                .trainerId(request.trainerId())
                .build());

        return new ClientCreatedResponse(clientMapper.toResponse(client), username, temporaryPassword);
    }

    private String generateUsername(String firstName, String lastName) {
        String base = (firstName + lastName).toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        if (base.length() > 16) base = base.substring(0, 16);
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private String generatePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    public List<ClientResponse> findAll() {
        return clientRepository.findAll().stream()
                .map(clientMapper::toResponse)
                .toList();
    }

    public ClientResponse findById(Long id) {
        return clientMapper.toResponse(getClientOrThrow(id));
    }

    @Transactional
    public ClientResponse update(Long id, UpdateClientRequest request) {
        Client client = getClientOrThrow(id);
        client.setFirstName(request.firstName());
        client.setLastName(request.lastName());
        client.setPhone(request.phone());
        client.setBirthDate(request.birthDate());
        client.setObjective(request.objective());
        client.setNotes(request.notes());
        return clientMapper.toResponse(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse uploadPhoto(Long id, org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) {
            throw new com.adrifit.backend.common.exception.BusinessException("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new com.adrifit.backend.common.exception.BusinessException("Only image files are allowed");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new com.adrifit.backend.common.exception.BusinessException("File size must not exceed 5 MB");
        }
        try {
            byte[] bytes = file.getBytes();
            String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
            String dataUrl = "data:" + contentType + ";base64," + base64;
            Client client = getClientOrThrow(id);
            client.setPhotoBase64(dataUrl);
            return clientMapper.toResponse(clientRepository.save(client));
        } catch (java.io.IOException e) {
            throw new com.adrifit.backend.common.exception.BusinessException("Failed to read uploaded file");
        }
    }

    @Transactional
    public void delete(Long id) {
        Client client = getClientOrThrow(id);
        Long userId = client.getUserId();
        clientRepository.delete(client);
        userRepository.deleteById(userId);
    }

    public Client getByUserId(Long userId) {
        return clientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found for current user"));
    }

    public Client getEntityById(Long id) {
        return getClientOrThrow(id);
    }

    private Client getClientOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
    }
}
