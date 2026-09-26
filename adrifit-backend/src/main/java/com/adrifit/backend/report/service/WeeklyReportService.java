package com.adrifit.backend.report.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.event.ClientDeletedEvent;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecurityUtils;
import com.adrifit.backend.email.domain.EmailType;
import com.adrifit.backend.email.service.EmailService;
import com.adrifit.backend.email.service.EmailTemplates;
import com.adrifit.backend.report.domain.ReportStatus;
import com.adrifit.backend.report.domain.WeeklyReport;
import com.adrifit.backend.report.dto.CoachFeedbackRequest;
import com.adrifit.backend.report.dto.CreateWeeklyReportRequest;
import com.adrifit.backend.report.dto.UpdateWeeklyReportRequest;
import com.adrifit.backend.report.dto.WeeklyReportResponse;
import com.adrifit.backend.photo.dto.PhotoDtos.PhotoResponse;
import com.adrifit.backend.photo.service.ProgressPhotoService;
import com.adrifit.backend.report.event.ReportReviewedEvent;
import com.adrifit.backend.report.mapper.WeeklyReportMapper;
import com.adrifit.backend.report.repository.WeeklyReportRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WeeklyReportService {

    private final WeeklyReportRepository reportRepository;
    private final WeeklyReportMapper reportMapper;
    private final ClientService clientService;
    private final EmailService emailService;
    private final EmailTemplates emailTemplates;
    private final ApplicationEventPublisher events;
    private final ProgressPhotoService photoService;

    public static final int MIN_PHOTOS = 4;
    public static final int MAX_PHOTOS = 6;

    public WeeklyReportService(WeeklyReportRepository reportRepository,
                               WeeklyReportMapper reportMapper,
                               ClientService clientService,
                               EmailService emailService,
                               EmailTemplates emailTemplates,
                               ApplicationEventPublisher events,
                               ProgressPhotoService photoService) {
        this.photoService = photoService;
        this.reportRepository = reportRepository;
        this.reportMapper = reportMapper;
        this.clientService = clientService;
        this.emailService = emailService;
        this.emailTemplates = emailTemplates;
        this.events = events;
    }

    @Transactional
    public WeeklyReportResponse create(Long clientId, CreateWeeklyReportRequest request) {
        Client client = clientService.assertCanAccess(clientId);
        WeeklyReport report = reportRepository.save(WeeklyReport.builder()
                .client(client)
                .weight(request.weight())
                .waist(request.waist())
                .bodyFat(request.bodyFat())
                .energyLevel(request.energyLevel())
                .dietAdherence(request.dietAdherence())
                .trainingAdherence(request.trainingAdherence())
                .comments(request.comments())
                .build());
        return reportMapper.toResponse(report);
    }

    /**
     * Client check-in: 4-6 photos, the weight and an optional comment. Nothing else is asked.
     */
    @Transactional
    public WeeklyReportResponse submitForCurrentClient(java.math.BigDecimal weight, String comments,
                                                       List<org.springframework.web.multipart.MultipartFile> files) {
        Client client = clientService.getCurrentClient();
        if (weight == null || weight.compareTo(java.math.BigDecimal.valueOf(20)) < 0
                || weight.compareTo(java.math.BigDecimal.valueOf(400)) > 0) {
            throw new BusinessException("Indica un peso válido (entre 20 y 400 kg)");
        }
        String comment = comments == null || comments.isBlank() ? null : comments.trim();
        if (comment != null && comment.length() > 1000) {
            throw new BusinessException("El comentario no puede superar los 1000 caracteres");
        }
        List<org.springframework.web.multipart.MultipartFile> photos = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (photos.size() < MIN_PHOTOS || photos.size() > MAX_PHOTOS) {
            throw new BusinessException("El seguimiento necesita entre " + MIN_PHOTOS + " y " + MAX_PHOTOS
                    + " fotos (has enviado " + photos.size() + ")");
        }
        WeeklyReport report = reportRepository.save(WeeklyReport.builder()
                .client(client)
                .weight(weight)
                .comments(comment)
                .build());
        List<PhotoResponse> stored = photoService.storeForReport(client.getId(), report.getId(), photos,
                java.time.LocalDate.now());
        return reportMapper.toResponse(report, stored);
    }

    public List<WeeklyReportResponse> findAll() {
        return withPhotos(reportRepository.findAllByOrderByCreatedAtDesc());
    }

    public List<WeeklyReportResponse> findPending() {
        return withPhotos(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING));
    }

    private List<WeeklyReportResponse> withPhotos(List<WeeklyReport> reports) {
        java.util.Map<Long, List<PhotoResponse>> photos = photoService.findForReports(
                reports.stream().map(WeeklyReport::getId).toList());
        return reports.stream()
                .map(r -> reportMapper.toResponse(r, photos.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    public List<WeeklyReportResponse> findByCurrentClient() {
        return findByClientId(clientService.getCurrentClientId());
    }

    public List<WeeklyReportResponse> findByClientId(Long clientId) {
        clientService.assertCanAccess(clientId);
        return withPhotos(reportRepository.findByClient_IdOrderByCreatedAtDesc(clientId));
    }

    public WeeklyReportResponse findById(Long id) {
        WeeklyReport report = getReportOrThrow(id);
        assertCanAccessReport(report);
        return withPhotos(List.of(report)).get(0);
    }

    @Transactional
    public WeeklyReportResponse update(Long id, UpdateWeeklyReportRequest request) {
        WeeklyReport report = getReportOrThrow(id);
        assertCanAccessReport(report);
        assertClientCanModify(report);
        report.setWeight(request.weight());
        report.setWaist(request.waist());
        report.setBodyFat(request.bodyFat());
        report.setEnergyLevel(request.energyLevel());
        report.setDietAdherence(request.dietAdherence());
        report.setTrainingAdherence(request.trainingAdherence());
        report.setComments(request.comments());
        return withPhotos(List.of(reportRepository.save(report))).get(0);
    }

    @Transactional
    public void delete(Long id) {
        WeeklyReport report = getReportOrThrow(id);
        assertCanAccessReport(report);
        assertClientCanModify(report);
        reportRepository.delete(report);
    }

    @Transactional
    public WeeklyReportResponse setCoachFeedback(Long id, CoachFeedbackRequest request) {
        WeeklyReport report = getReportOrThrow(id);
        boolean firstReview = report.getStatus() != ReportStatus.REVIEWED;
        report.setCoachFeedback(request.coachFeedback());
        report.setStatus(ReportStatus.REVIEWED);
        report.setReviewedAt(Instant.now());
        WeeklyReport saved = reportRepository.save(report);

        Client client = report.getClient();
        events.publishEvent(new ReportReviewedEvent(client.getId(), saved.getId()));
        emailService.sendToClient(client.getId(), EmailType.REPORT_FEEDBACK,
                firstReview ? "Tienes nuevo feedback de tu entrenador" : "Tu entrenador ha actualizado su feedback",
                emailTemplates.reportFeedback(client.getFirstName(), request.coachFeedback()));
        return withPhotos(List.of(saved)).get(0);
    }

    @EventListener
    @Transactional
    public void onClientDeleted(ClientDeletedEvent event) {
        reportRepository.deleteAll(reportRepository.findByClient_Id(event.clientId()));
    }

    private WeeklyReport getReportOrThrow(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));
    }

    private void assertCanAccessReport(WeeklyReport report) {
        clientService.assertCanAccess(report.getClient().getId());
    }

    /** Once the trainer has reviewed a report the client can no longer change it. */
    private void assertClientCanModify(WeeklyReport report) {
        if (!SecurityUtils.isTrainer() && report.getStatus() == ReportStatus.REVIEWED) {
            throw new BusinessException("Este seguimiento ya ha sido revisado y no se puede modificar");
        }
    }
}
