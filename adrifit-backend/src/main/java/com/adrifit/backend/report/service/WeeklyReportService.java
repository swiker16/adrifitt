package com.adrifit.backend.report.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecurityUtils;
import com.adrifit.backend.report.domain.ReportStatus;
import com.adrifit.backend.report.domain.WeeklyReport;
import com.adrifit.backend.report.dto.CoachFeedbackRequest;
import com.adrifit.backend.report.dto.CreateWeeklyReportRequest;
import com.adrifit.backend.report.dto.UpdateWeeklyReportRequest;
import com.adrifit.backend.report.dto.WeeklyReportResponse;
import com.adrifit.backend.report.mapper.WeeklyReportMapper;
import com.adrifit.backend.report.repository.WeeklyReportRepository;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.service.UserService;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WeeklyReportService {

    private final WeeklyReportRepository reportRepository;
    private final WeeklyReportMapper reportMapper;
    private final ClientService clientService;
    private final UserService userService;

    public WeeklyReportService(WeeklyReportRepository reportRepository,
                               WeeklyReportMapper reportMapper,
                               ClientService clientService,
                               UserService userService) {
        this.reportRepository = reportRepository;
        this.reportMapper = reportMapper;
        this.clientService = clientService;
        this.userService = userService;
    }

    @Transactional
    public WeeklyReportResponse create(Long clientId, CreateWeeklyReportRequest request) {
        assertCanAccessClient(clientId);
        Client client = clientService.getEntityById(clientId);
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
        Long clientId = getCurrentClientId();
        return create(clientId, request);
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
        return findByClientId(getCurrentClientId());
    }

    public List<WeeklyReportResponse> findByClientId(Long clientId) {
        assertCanAccessClient(clientId);
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
        reportRepository.delete(report);
    }

    @Transactional
    public WeeklyReportResponse setCoachFeedback(Long id, CoachFeedbackRequest request) {
        WeeklyReport report = getReportOrThrow(id);
        report.setCoachFeedback(request.coachFeedback());
        report.setStatus(ReportStatus.REVIEWED);
        report.setReviewedAt(Instant.now());
        return reportMapper.toResponse(reportRepository.save(report));
    }

    private WeeklyReport getReportOrThrow(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));
    }

    private void assertCanAccessClient(Long clientId) {
        if (SecurityUtils.isTrainer()) {
            return;
        }
        if (!getCurrentClientId().equals(clientId)) {
            throw new AccessDeniedException("You can only access your own reports");
        }
    }

    private void assertCanAccessReport(WeeklyReport report) {
        assertCanAccessClient(report.getClient().getId());
    }

    private Long getCurrentClientId() {
        User currentUser = userService.getCurrentUser();
        return clientService.getByUserId(currentUser.getId()).getId();
    }
}
