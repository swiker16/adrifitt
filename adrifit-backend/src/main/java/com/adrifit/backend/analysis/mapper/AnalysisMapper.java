package com.adrifit.backend.analysis.mapper;

import com.adrifit.backend.analysis.domain.ClientAnalysis;
import com.adrifit.backend.analysis.dto.AnalysisResponse;
import org.springframework.stereotype.Component;

@Component
public class AnalysisMapper {

    public AnalysisResponse toResponse(ClientAnalysis a, String baseUrl) {
        return toResponse(a, baseUrl, null);
    }

    public AnalysisResponse toResponse(ClientAnalysis a, String baseUrl, String clientName) {
        String downloadUrl = baseUrl + "/content?disposition=attachment";
        String viewUrl = baseUrl + "/content";
        return new AnalysisResponse(
                a.getId(),
                a.getClientId(),
                clientName,
                a.getTitle(),
                a.getAnalysisDate(),
                a.getClientComment(),
                a.getTrainerInternalNote(),
                a.getUploadedAt(),
                a.getReviewedAt(),
                a.getStatus(),
                a.getOriginalFileName(),
                a.getContentType(),
                a.getFileSize(),
                downloadUrl,
                viewUrl
        );
    }
}
