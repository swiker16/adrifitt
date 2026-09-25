package com.adrifit.backend.photo.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.event.ClientDeletedEvent;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecurityUtils;
import com.adrifit.backend.common.storage.FileStorageService;
import com.adrifit.backend.photo.domain.PhotoPose;
import com.adrifit.backend.photo.domain.ProgressPhoto;
import com.adrifit.backend.photo.dto.PhotoDtos.PhotoResponse;
import com.adrifit.backend.photo.repository.ProgressPhotoRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ProgressPhotoService {

    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private final ProgressPhotoRepository repository;
    private final FileStorageService storage;
    private final ClientService clientService;

    public ProgressPhotoService(ProgressPhotoRepository repository,
                                FileStorageService storage,
                                ClientService clientService) {
        this.repository = repository;
        this.storage = storage;
        this.clientService = clientService;
    }

    @Transactional
    public PhotoResponse uploadMine(MultipartFile file, LocalDate takenOn, PhotoPose pose, String notes) {
        Client client = clientService.getCurrentClient();
        String contentType = detectImageType(file);
        if (takenOn == null) {
            takenOn = LocalDate.now();
        }
        if (takenOn.isAfter(LocalDate.now())) {
            throw new BusinessException("La fecha de la foto no puede ser futura");
        }
        if (notes != null && notes.length() > 500) {
            throw new BusinessException("Las notas no pueden superar los 500 caracteres");
        }

        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String key = "photos/" + client.getId() + "/" + UUID.randomUUID() + extension;
        try (InputStream in = file.getInputStream()) {
            storage.store(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer la imagen");
        }

        ProgressPhoto photo = repository.save(ProgressPhoto.builder()
                .clientId(client.getId())
                .takenOn(takenOn)
                .pose(pose != null ? pose : PhotoPose.FRONT)
                .notes(notes)
                .storageKey(key)
                .contentType(contentType)
                .fileSize(file.getSize())
                .build());
        return toResponse(photo);
    }

    public List<PhotoResponse> findMine() {
        return findForClient(clientService.getCurrentClientId());
    }

    public List<PhotoResponse> findForClient(Long clientId) {
        clientService.assertCanAccess(clientId);
        return repository.findByClientIdOrderByTakenOnDescIdDesc(clientId).stream().map(this::toResponse).toList();
    }

    /** Returns the photo if the current user may see it (its owner or the trainer). */
    public ProgressPhoto getAccessible(Long id) {
        ProgressPhoto photo = getOrThrow(id);
        if (!SecurityUtils.isTrainer() && !photo.getClientId().equals(clientService.getCurrentClientId())) {
            throw new ResourceNotFoundException("Photo not found: " + id);
        }
        return photo;
    }

    public InputStream openContent(ProgressPhoto photo) {
        return storage.load(photo.getStorageKey());
    }

    @Transactional
    public void deleteMine(Long id) {
        ProgressPhoto photo = getOrThrow(id);
        if (!photo.getClientId().equals(clientService.getCurrentClientId())) {
            throw new ResourceNotFoundException("Photo not found: " + id);
        }
        repository.delete(photo);
        storage.delete(photo.getStorageKey());
    }

    @Transactional
    public PhotoResponse comment(Long id, String comment) {
        ProgressPhoto photo = getOrThrow(id);
        photo.setTrainerComment(comment == null || comment.isBlank() ? null : comment.trim());
        return toResponse(repository.save(photo));
    }

    public long countForClient(Long clientId) {
        return repository.countByClientId(clientId);
    }

    @EventListener
    @Transactional
    public void onClientDeleted(ClientDeletedEvent event) {
        List<ProgressPhoto> photos = repository.findByClientIdOrderByTakenOnDescIdDesc(event.clientId());
        repository.deleteAll(photos);
        photos.forEach(p -> storage.delete(p.getStorageKey()));
    }

    /**
     * Trusts the file signature, not the declared content type.
     */
    private String detectImageType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Selecciona una imagen");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("La imagen no puede superar los 10 MB");
        }
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(head, 0, head.length);
            if (read < 12) {
                throw new BusinessException("El archivo no es una imagen válida");
            }
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer la imagen");
        }
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "image/png";
        }
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "image/webp";
        }
        throw new BusinessException("Formato no soportado. Usa JPG, PNG o WEBP");
    }

    private ProgressPhoto getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Photo not found: " + id));
    }

    private PhotoResponse toResponse(ProgressPhoto p) {
        return new PhotoResponse(p.getId(), p.getClientId(), p.getTakenOn(), p.getPose(), p.getNotes(),
                p.getTrainerComment(), p.getContentType(), p.getFileSize(), p.getUploadedAt(),
                "/api/photos/" + p.getId() + "/content");
    }
}
