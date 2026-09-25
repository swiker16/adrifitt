package com.adrifit.backend.client.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.dto.ClientCreatedResponse;
import com.adrifit.backend.client.dto.ClientResponse;
import com.adrifit.backend.client.dto.CreateClientRequest;
import com.adrifit.backend.client.dto.UpdateClientRequest;
import com.adrifit.backend.client.mapper.ClientMapper;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.common.event.ClientDeletedEvent;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecurityUtils;
import com.adrifit.backend.user.domain.Role;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ClientService {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClientMapper clientMapper;
    private final ApplicationEventPublisher events;

    public ClientService(ClientRepository clientRepository,
                         UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         ClientMapper clientMapper,
                         ApplicationEventPublisher events) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clientMapper = clientMapper;
        this.events = events;
    }

    @Transactional
    public ClientCreatedResponse create(CreateClientRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Ya existe un usuario con ese email");
        }

        String username = generateUsername(request.firstName(), request.lastName());
        String temporaryPassword = generatePassword();

        User user = userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(temporaryPassword))
                .role(Role.CLIENT)
                .mustChangePassword(true)
                .build());

        Long trainerId = request.trainerId() != null ? request.trainerId() : currentUserIdOrNull();

        Client client = clientRepository.save(Client.builder()
                .userId(user.getId())
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .phone(request.phone())
                .birthDate(request.birthDate())
                .objective(request.objective())
                .notes(request.notes())
                .trainerId(trainerId)
                .build());

        return new ClientCreatedResponse(clientMapper.toResponse(client), username, temporaryPassword);
    }

    /**
     * Generates a new temporary password for the client's user. Returns it in plain text so the
     * caller can show / email it once.
     */
    @Transactional
    public String resetPassword(Long clientId) {
        Client client = getClientOrThrow(clientId);
        User user = userRepository.findById(client.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for client: " + clientId));
        String temporaryPassword = generatePassword();
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        return temporaryPassword;
    }

    private String generateUsername(String firstName, String lastName) {
        String base = java.text.Normalizer.normalize(firstName + lastName, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        if (base.isEmpty()) base = "cliente";
        if (base.length() > 16) base = base.substring(0, 16);
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
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

    public ClientResponse findMe() {
        return clientMapper.toResponse(getCurrentClient());
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
    public ClientResponse uploadPhoto(Long id, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("Only image files are allowed");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("File size must not exceed 5 MB");
        }
        try {
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            Client client = getClientOrThrow(id);
            client.setPhotoBase64("data:" + contentType + ";base64," + base64);
            return clientMapper.toResponse(clientRepository.save(client));
        } catch (IOException e) {
            throw new BusinessException("Failed to read uploaded file");
        }
    }

    @Transactional
    public void delete(Long id) {
        Client client = getClientOrThrow(id);
        Long userId = client.getUserId();
        // Every module removes the rows that reference this client before the client itself goes.
        events.publishEvent(new ClientDeletedEvent(client.getId(), userId));
        clientRepository.delete(client);
        clientRepository.flush();
        userRepository.deleteById(userId);
    }

    public Client getByUserId(Long userId) {
        return clientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found for current user"));
    }

    public Client getEntityById(Long id) {
        return getClientOrThrow(id);
    }

    /** Client profile of the authenticated CLIENT user. */
    public Client getCurrentClient() {
        User user = userRepository.findByUsername(SecurityUtils.getCurrentUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
        return getByUserId(user.getId());
    }

    public Long getCurrentClientId() {
        return getCurrentClient().getId();
    }

    /**
     * Trainers can access any client; a client only itself. Also validates that the client exists.
     */
    public Client assertCanAccess(Long clientId) {
        if (SecurityUtils.isTrainer()) {
            return getClientOrThrow(clientId);
        }
        Client current = getCurrentClient();
        if (!current.getId().equals(clientId)) {
            throw new AccessDeniedException("You can only access your own data");
        }
        return current;
    }

    private Long currentUserIdOrNull() {
        try {
            return userRepository.findByUsername(SecurityUtils.getCurrentUsername()).map(User::getId).orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Client getClientOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
    }
}
