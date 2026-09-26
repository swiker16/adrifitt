package com.adrifit.backend.testimonial.controller;

import com.adrifit.backend.testimonial.dto.TestimonialDtos.CreateTestimonialRequest;
import com.adrifit.backend.testimonial.dto.TestimonialDtos.PublicTestimonial;
import com.adrifit.backend.testimonial.dto.TestimonialDtos.TestimonialResponse;
import com.adrifit.backend.testimonial.dto.TestimonialDtos.VisibilityRequest;
import com.adrifit.backend.testimonial.service.TestimonialService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestimonialController {

    private final TestimonialService service;

    public TestimonialController(TestimonialService service) {
        this.service = service;
    }

    /** Public: landing page. */
    @GetMapping("/api/public/testimonials")
    public ResponseEntity<List<PublicTestimonial>> findPublic() {
        return ResponseEntity.ok(service.findPublic());
    }

    @GetMapping("/api/testimonials/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<TestimonialResponse> findMine() {
        return ResponseEntity.ok(service.findMine());
    }

    @PostMapping("/api/testimonials/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<TestimonialResponse> create(@Valid @RequestBody CreateTestimonialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMine(request));
    }

    @GetMapping("/api/testimonials")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<TestimonialResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @PatchMapping("/api/testimonials/{id}/visibility")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<TestimonialResponse> setVisibility(@PathVariable Long id,
                                                             @Valid @RequestBody VisibilityRequest request) {
        return ResponseEntity.ok(service.setVisibility(id, request.visible()));
    }
}
