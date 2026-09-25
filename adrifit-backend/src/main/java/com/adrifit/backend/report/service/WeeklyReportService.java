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

    public WeeklyReportService(WeeklyReportRepository reportRepository,
                               WeeklyReportMapper reportMapper,
                               ClientService clientService,
                               EmailService emailService,
                               EmailTemplates emailTemplates,
                               ApplicationEventPublisher events) {
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

    @Transactional
    public WeeklyReportResponse createForCurrentClient(CreateWeeklyReportRequest request) {
        return create(clientService.getCurrentClientId(), request);
    }

    public List<WeeklyReportResponse> findAll() {
        return reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(reportMapper::toResponse)
                .toList();
    }

    public List<WeeklyReportResponse> findPending() {
        return reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING).stream()
                .map(reportMapper::toResponse)
                .toList();
    }

    public List<WeeklyReportResponse> findByCurrentClient() {
        return findByClientId(clientService.getCurrentClientId());
    }

    public List<WeeklyReportResponse> findByClientId(Long clientId) {
        clientService.assertCanAccess(clientId);
        return reportRepository.findByClient_IdOrderByCreatedAtDesc(clientId).stream()
                .map(reportMapper::toResponse)
                .toList();
    }

    public WeeklyReportResponse findById(Long id) {
        WeeklyReport report = getReportOrThrow(id);
        assertCanAccessReport(report);
        return reportMapper.toResponse(report);
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
        return reportMapper.toResponse(reportRepository.save(report));
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
        return reportMapper.toResponse(saved);
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
