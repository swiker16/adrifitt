package com.adrifit.backend.analysis.service;

import com.adrifit.backend.analysis.domain.AnalysisStatus;
import com.adrifit.backend.analysis.domain.ClientAnalysis;
import com.adrifit.backend.analysis.dto.AnalysisResponse;
import com.adrifit.backend.analysis.dto.ReviewAnalysisRequest;
import com.adrifit.backend.analysis.dto.UploadAnalysisRequest;
import com.adrifit.backend.analysis.mapper.AnalysisMapper;
import com.adrifit.backend.analysis.repository.ClientAnalysisRepository;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecurityUtils;
import com.adrifit.backend.common.storage.FileStorageService;
import com.adrifit.backend.user.service.UserService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final long MAX_FILE_SIZE = 15L * 1024 * 1024;
    private static final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF

    private final ClientAnalysisRepository repository;
    private final FileStorageService storage;
    private final AnalysisMapper mapper;
    private final ClientService clientService;
    private final UserService userService;

    public AnalysisService(ClientAnalysisRepository repository,
                           FileStorageService storage,
                           AnalysisMapper mapper,
                           ClientService clientService,
                           UserService userService) {
        this.repository = repository;
        this.storage = storage;
        this.mapper = mapper;
        this.clientService = clientService;
        this.userService = userService;
    }

    @Transactional
    public AnalysisResponse uploadForCurrentClient(UploadAnalysisRequest request, MultipartFile file) {
        Long clientId = getCurrentClientId();
        validateFile(file);

        String uuid = UUID.randomUUID().toString();
        String ext = ".pdf";
        String storedName = uuid + ext;

        ClientAnalysis analysis = ClientAnalysis.builder()
                .clientId(clientId)
                .title(request.title())
                .analysisDate(request.analysisDate())
                .clientComment(request.clientComment())
                .originalFileName(sanitizeFileName(file.getOriginalFilename()))
                .storedFileName(storedName)
                .storageKey("analyses/" + clientId + "/tmp/" + storedName)
                .contentType("application/pdf")
                .fileSize(file.getSize())
                .storageProvider(storage.providerName())
                .uploadedAt(Instant.now())
                .status(AnalysisStatus.UPLOADED)
                .active(true)
                .build();

        analysis = repository.save(analysis);

        String finalKey = "analyses/" + clientId + "/" + analysis.getId() + "/" + storedName;
        analysis.setStorageKey(finalKey);

        try (InputStream stream = file.getInputStream()) {
            storage.store(finalKey, stream, file.getSize(), "application/pdf");
        } catch (IOException | RuntimeException e) {
            repository.delete(analysis);
            log.error("Failed to store analysis file for clientId={}", clientId, e);
            throw new BusinessException("Failed to store analysis file");
        }

        analysis = repository.save(analysis);
        return mapper.toResponse(analysis, clientContentBaseUrl(analysis.getId()));
    }

    public List<AnalysisResponse> getMyAnalyses() {
        Long clientId = getCurrentClientId();
        return repository.findByClientIdAndActiveTrueOrderByUploadedAtDesc(clientId)
                .stream().map(a -> mapper.toResponse(a, clientContentBaseUrl(a.getId()))).toList();
    }

    public AnalysisResponse getMyAnalysis(Long analysisId) {
        Long clientId = getCurrentClientId();
        ClientAnalysis a = repository.findByIdAndClientIdAndActiveTrue(analysisId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found: " + analysisId));
        return mapper.toResponse(a, clientContentBaseUrl(a.getId()));
    }

    public InputStream getContentForClient(Long analysisId) {
        Long clientId = getCurrentClientId();
        ClientAnalysis a = repository.findByIdAndClientIdAndActiveTrue(analysisId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found: " + analysisId));
        return storage.load(a.getStorageKey());
    }

    @Transactional
    public void deleteMyAnalysis(Long analysisId) {
        Long clientId = getCurrentClientId();
        ClientAnalysis a = repository.findByIdAndClientIdAndActiveTrue(analysisId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found: " + analysisId));
        if (a.getStatus() == AnalysisStatus.REVIEWED) {
            throw new BusinessException("No se puede eliminar una analítica ya revisada.");
        }
        String key = a.getStorageKey();
        a.setActive(false);
        repository.save(a);
        storage.delete(key);
    }

    public List<AnalysisResponse> getAnalysesForClient(Long clientId) {
        assertTrainerAccessToClient(clientId);
        return repository.findByClientIdAndActiveTrueOrderByUploadedAtDesc(clientId)
                .stream().map(a -> mapper.toResponse(a, trainerContentBaseUrl(a.getId()))).toList();
    }

    public AnalysisResponse getAnalysisForTrainer(Long analysisId) {
        ClientAnalysis a = getActiveOrThrow(analysisId);
        assertTrainerAccessToClient(a.getClientId());
        return mapper.toResponse(a, trainerContentBaseUrl(a.getId()));
    }

    public InputStream getContentForTrainer(Long analysisId) {
        ClientAnalysis a = getActiveOrThrow(analysisId);
        assertTrainerAccessToClient(a.getClientId());
        return storage.load(a.getStorageKey());
    }

    @Transactional
    public AnalysisResponse review(Long analysisId, ReviewAnalysisRequest request) {
        ClientAnalysis a = getActiveOrThrow(analysisId);
        assertTrainerAccessToClient(a.getClientId());
        a.setStatus(AnalysisStatus.REVIEWED);
        a.setReviewedAt(Instant.now());
        a.setReviewedByUserId(userService.getCurrentUser().getId());
        if (request.trainerInternalNote() != null) {
            a.setTrainerInternalNote(request.trainerInternalNote());
        }
        return mapper.toResponse(repository.save(a), trainerContentBaseUrl(a.getId()));
    }

    /** Trainer: every analysis of every client (optionally only the ones pending review). */
    public List<AnalysisResponse> findAllForTrainer(AnalysisStatus status) {
        List<ClientAnalysis> list = status != null
                ? repository.findByStatusAndActiveTrueOrderByUploadedAtDesc(status)
                : repository.findByActiveTrueOrderByUploadedAtDesc();
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        return list.stream().map(a -> mapper.toResponse(a, trainerContentBaseUrl(a.getId()),
                names.computeIfAbsent(a.getClientId(), id -> {
                    try {
                        com.adrifit.backend.client.domain.Client c = clientService.getEntityById(id);
                        return c.getFirstName() + " " + c.getLastName();
                    } catch (ResourceNotFoundException ex) {
                        return null;
                    }
                }))).toList();
    }

    @org.springframework.context.event.EventListener
    @Transactional
    public void onClientDeleted(com.adrifit.backend.common.event.ClientDeletedEvent event) {
        List<ClientAnalysis> list = repository.findByClientId(event.clientId());
        repository.deleteAll(list);
        list.forEach(a -> storage.delete(a.getStorageKey()));
    }

    public long countPendingAnalyses() {
        return repository.countByStatusAndActiveTrue(AnalysisStatus.UPLOADED);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("El archivo no puede estar vacío.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("El archivo supera el tamaño máximo de 15 MB.");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.equals("application/pdf")) {
            throw new BusinessException("Solo se permiten archivos PDF.");
        }
        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException("La extensión del archivo debe ser .pdf");
        }
        try {
            byte[] header = file.getBytes();
            if (header.length < 4 || !Arrays.equals(Arrays.copyOf(header, 4), PDF_MAGIC)) {
                throw new BusinessException("El archivo no es un PDF válido.");
            }
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el archivo.");
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "documento.pdf";
        String sanitized = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return sanitized.length() > 200 ? sanitized.substring(0, 200) : sanitized;
    }

    private ClientAnalysis getActiveOrThrow(Long id) {
        return repository.findById(id)
                .filter(ClientAnalysis::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found: " + id));
    }

    private void assertTrainerAccessToClient(Long clientId) {
        if (!SecurityUtils.isTrainer()) {
            throw new AccessDeniedException("Access denied");
        }
        clientService.getEntityById(clientId);
    }

    private Long getCurrentClientId() {
        return clientService.getByUserId(userService.getCurrentUser().getId()).getId();
    }

    private String clientContentBaseUrl(Long analysisId) {
        return "/api/client/analyses/" + analysisId;
    }

    private String trainerContentBaseUrl(Long analysisId) {
        return "/api/analyses/" + analysisId;
    }
}
