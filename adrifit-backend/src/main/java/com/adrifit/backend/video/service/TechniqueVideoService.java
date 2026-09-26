package com.adrifit.backend.video.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.event.ClientDeletedEvent;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecurityUtils;
import com.adrifit.backend.common.storage.FileStorageService;
import com.adrifit.backend.notification.event.NotificationEvents.VideoReviewed;
import com.adrifit.backend.notification.event.NotificationEvents.VideoUploaded;
import com.adrifit.backend.video.domain.TechniqueVideo;
import com.adrifit.backend.video.domain.VideoSource;
import com.adrifit.backend.video.dto.VideoDtos.StreamLink;
import com.adrifit.backend.video.dto.VideoDtos.VideoResponse;
import com.adrifit.backend.video.repository.TechniqueVideoRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Exercise technique videos. The client records a set so the trainer can correct the
 * technique; the trainer can also send demonstration videos. Files live in private storage and
 * are played through short-lived signed links.
 */
@Service
@Transactional(readOnly = true)
public class TechniqueVideoService {

    private final TechniqueVideoRepository repository;
    private final ClientRepository clientRepository;
    private final ClientService clientService;
    private final FileStorageService storage;
    private final VideoLinkSigner signer;
    private final ApplicationEventPublisher events;
    private final long maxBytes;

    public TechniqueVideoService(TechniqueVideoRepository repository,
                                 ClientRepository clientRepository,
                                 ClientService clientService,
                                 FileStorageService storage,
                                 VideoLinkSigner signer,
                                 ApplicationEventPublisher events,
                                 @Value("${adrifit.videos.max-size-mb:200}") long maxSizeMb) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.clientService = clientService;
        this.storage = storage;
        this.signer = signer;
        this.events = events;
        this.maxBytes = maxSizeMb * 1024 * 1024;
    }

    // ── Upload ──────────────────────────────────────────────────────────────

    /** The client sends a video of an exercise for the trainer to correct. */
    @Transactional
    public VideoResponse uploadMine(MultipartFile file, String exerciseName, String note, Integer durationSeconds) {
        Client client = clientService.getCurrentClient();
        return toResponse(store(client, VideoSource.CLIENT, file, exerciseName, note, durationSeconds), client);
    }

    /** The trainer sends a demonstration video to a client. */
    @Transactional
    public VideoResponse uploadForClient(Long clientId, MultipartFile file, String exerciseName, String note,
                                         Integer durationSeconds) {
        Client client = clientService.assertCanAccess(clientId);
        return toResponse(store(client, VideoSource.TRAINER, file, exerciseName, note, durationSeconds), client);
    }

    private TechniqueVideo store(Client client, VideoSource source, MultipartFile file, String exerciseName,
                                 String note, Integer durationSeconds) {
        String exercise = exerciseName == null ? "" : exerciseName.trim();
        if (exercise.isEmpty()) {
            throw new BusinessException("Indica el ejercicio del vídeo");
        }
        if (exercise.length() > 150) {
            throw new BusinessException("El nombre del ejercicio es demasiado largo");
        }
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 1000) {
            throw new BusinessException("La nota no puede superar los 1000 caracteres");
        }
        String contentType = detectVideoType(file);
        String extension = switch (contentType) {
            case "video/quicktime" -> ".mov";
            case "video/webm" -> ".webm";
            default -> ".mp4";
        };
        String key = "videos/" + client.getId() + "/" + UUID.randomUUID() + extension;
        try (InputStream in = file.getInputStream()) {
            storage.store(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el vídeo");
        }
        boolean validDuration = durationSeconds != null && durationSeconds > 0 && durationSeconds < 36_000;
        TechniqueVideo video = repository.save(TechniqueVideo.builder()
                .clientId(client.getId())
                .source(source)
                .exerciseName(exercise)
                .note(cleanNote)
                .storageKey(key)
                .contentType(contentType)
                .fileSize(file.getSize())
                .durationSeconds(validDuration ? durationSeconds : null)
                .build());
        events.publishEvent(new VideoUploaded(client.getId(), source == VideoSource.TRAINER, exercise));
        return video;
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    public List<VideoResponse> findMine() {
        Client client = clientService.getCurrentClient();
        return repository.findByClientIdOrderByCreatedAtDesc(client.getId()).stream()
                .map(v -> toResponse(v, client)).toList();
    }

    public List<VideoResponse> findForClient(Long clientId) {
        Client client = clientService.assertCanAccess(clientId);
        return repository.findByClientIdOrderByCreatedAtDesc(clientId).stream().map(v -> toResponse(v, client)).toList();
    }

    /** Trainer inbox: pending corrections (oldest first), or every video (newest first). */
    public List<VideoResponse> findAll(boolean pendingOnly) {
        List<TechniqueVideo> videos = pendingOnly
                ? repository.findBySourceAndReviewedAtIsNullOrderByCreatedAtAsc(VideoSource.CLIENT)
                : repository.findAllByOrderByCreatedAtDesc();
        Map<Long, Client> clients = clients(videos.stream().map(TechniqueVideo::getClientId).toList());
        return videos.stream().map(v -> toResponse(v, clients.get(v.getClientId()))).toList();
    }

    public long countPendingReview() {
        return repository.countBySourceAndReviewedAtIsNull(VideoSource.CLIENT);
    }

    public long countUnseenByClient(Long clientId) {
        return repository.countUnseenByClient(clientId);
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    @Transactional
    public VideoResponse review(Long id, String feedback) {
        TechniqueVideo video = getOrThrow(id);
        if (video.getSource() != VideoSource.CLIENT) {
            throw new BusinessException("Solo se corrigen los vídeos enviados por el cliente");
        }
        video.setFeedback(feedback.trim());
        video.setReviewedAt(Instant.now());
        video.setClientSeenAt(null);
        repository.save(video);
        events.publishEvent(new VideoReviewed(video.getClientId(), video.getExerciseName()));
        return toResponse(video, clientRepository.findById(video.getClientId()).orElse(null));
    }

    /** The client opened the videos page: the trainer's videos and corrections are no longer new. */
    @Transactional
    public void markSeenByCurrentClient() {
        Long clientId = clientService.getCurrentClientId();
        Instant now = Instant.now();
        repository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .filter(TechniqueVideoService::isNewForClient)
                .forEach(v -> v.setClientSeenAt(now));
    }

    /** Clients delete their own recordings; the trainer can delete any video. */
    @Transactional
    public void delete(Long id) {
        TechniqueVideo video = getAccessible(id);
        if (!SecurityUtils.isTrainer() && video.getSource() != VideoSource.CLIENT) {
            throw new BusinessException("Solo puedes borrar los vídeos que has enviado tú");
        }
        repository.delete(video);
        storage.delete(video.getStorageKey());
    }

    // ── Playback ────────────────────────────────────────────────────────────

    public StreamLink streamLink(Long id) {
        TechniqueVideo video = getAccessible(id);
        long exp = signer.expiry().getEpochSecond();
        String base = "/api/videos/" + video.getId() + "/stream?exp=" + exp + "&sig=" + signer.sign(video.getId(), exp);
        // ngsw-bypass: the Angular service worker must not proxy (or cache) video range requests.
        return new StreamLink(base + "&ngsw-bypass=true", base + "&download=true&ngsw-bypass=true",
                Instant.ofEpochSecond(exp));
    }

    /** Resolves a signed link; an invalid or expired signature looks like a missing video. */
    public TechniqueVideo getBySignedLink(Long id, long exp, String sig) {
        if (!signer.isValid(id, exp, sig)) {
            throw new ResourceNotFoundException("Video not found: " + id);
        }
        return getOrThrow(id);
    }

    public Resource openContent(TechniqueVideo video) {
        return storage.loadResource(video.getStorageKey());
    }

    @EventListener
    @Transactional
    public void onClientDeleted(ClientDeletedEvent event) {
        List<TechniqueVideo> videos = repository.findByClientIdOrderByCreatedAtDesc(event.clientId());
        repository.deleteAll(videos);
        videos.forEach(v -> storage.delete(v.getStorageKey()));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static boolean isNewForClient(TechniqueVideo v) {
        return v.getClientSeenAt() == null && (v.getSource() == VideoSource.TRAINER || v.getReviewedAt() != null);
    }

    private TechniqueVideo getAccessible(Long id) {
        TechniqueVideo video = getOrThrow(id);
        if (!SecurityUtils.isTrainer() && !video.getClientId().equals(clientService.getCurrentClientId())) {
            throw new ResourceNotFoundException("Video not found: " + id);
        }
        return video;
    }

    private TechniqueVideo getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Video not found: " + id));
    }

    private Map<Long, Client> clients(Collection<Long> ids) {
        return clientRepository.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));
    }

    /**
     * Trusts the file signature, not the declared type: MP4/MOV (ISO base media "ftyp" box)
     * and WebM (EBML header). Covers iPhone, Android and desktop recordings.
     */
    private String detectVideoType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Selecciona un vídeo");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("El vídeo no puede superar los " + (maxBytes / 1024 / 1024)
                    + " MB. Graba un clip más corto (unos 30-60 segundos).");
        }
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            if (in.readNBytes(head, 0, head.length) < 12) {
                throw new BusinessException("El archivo no es un vídeo válido");
            }
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el vídeo");
        }
        if (head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
            return head[8] == 'q' && head[9] == 't' ? "video/quicktime" : "video/mp4";
        }
        if ((head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45 && (head[2] & 0xFF) == 0xDF && (head[3] & 0xFF) == 0xA3) {
            return "video/webm";
        }
        throw new BusinessException("Formato no soportado. Usa MP4, MOV (iPhone) o WEBM");
    }

    private VideoResponse toResponse(TechniqueVideo v, Client client) {
        String name = client == null ? null : (client.getFirstName() + " " + client.getLastName()).trim();
        return new VideoResponse(v.getId(), v.getClientId(), name, v.getSource(), v.getExerciseName(), v.getNote(),
                v.getContentType(), v.getFileSize(), v.getDurationSeconds(), v.getFeedback(), v.getReviewedAt(),
                isNewForClient(v), v.getCreatedAt());
    }
}
