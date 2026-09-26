package com.adrifit.backend.testimonial.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.event.ClientDeletedEvent;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.subscription.service.SubscriptionService;
import com.adrifit.backend.testimonial.domain.Testimonial;
import com.adrifit.backend.testimonial.dto.TestimonialDtos.CreateTestimonialRequest;
import com.adrifit.backend.testimonial.dto.TestimonialDtos.PublicTestimonial;
import com.adrifit.backend.testimonial.dto.TestimonialDtos.TestimonialResponse;
import com.adrifit.backend.testimonial.repository.TestimonialRepository;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TestimonialService {

    private final TestimonialRepository repository;
    private final ClientService clientService;
    private final SubscriptionService subscriptionService;

    public TestimonialService(TestimonialRepository repository,
                              ClientService clientService,
                              SubscriptionService subscriptionService) {
        this.repository = repository;
        this.clientService = clientService;
        this.subscriptionService = subscriptionService;
    }

    public List<PublicTestimonial> findPublic() {
        return repository.findByVisibleTrueOrderByCreatedAtDesc().stream()
                .map(t -> new PublicTestimonial(t.getId(), t.getAuthorName(), t.getPlanName(), t.getRating(),
                        t.getContent(), t.getCreatedAt()))
                .toList();
    }

    public TestimonialResponse findMine() {
        Client client = clientService.getCurrentClient();
        return repository.findByClientId(client.getId()).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Todavía no has publicado tu reseña"));
    }

    /** One review per client, ever. */
    @Transactional
    public TestimonialResponse createMine(CreateTestimonialRequest request) {
        Client client = clientService.getCurrentClient();
        if (repository.existsByClientId(client.getId())) {
            throw new BusinessException("Ya has publicado tu reseña. Solo se puede publicar una vez.");
        }
        String lastInitial = client.getLastName() == null || client.getLastName().isBlank()
                ? "" : " " + client.getLastName().trim().substring(0, 1).toUpperCase() + ".";
        try {
            Testimonial saved = repository.saveAndFlush(Testimonial.builder()
                    .clientId(client.getId())
                    .authorName(client.getFirstName().trim() + lastInitial)
                    .planName(subscriptionService.findCurrent(client.getId()).map(s -> s.getPlan()).map(Plan::getName).orElse(null))
                    .rating(request.rating())
                    .content(request.content().trim())
                    .visible(true)
                    .build());
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Ya has publicado tu reseña. Solo se puede publicar una vez.");
        }
    }

    public List<TestimonialResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public TestimonialResponse setVisibility(Long id, boolean visible) {
        Testimonial t = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Testimonial not found: " + id));
        t.setVisible(visible);
        return toResponse(repository.save(t));
    }

    @EventListener
    @Transactional
    public void onClientDeleted(ClientDeletedEvent event) {
        repository.findByClientId(event.clientId()).ifPresent(repository::delete);
    }

    private TestimonialResponse toResponse(Testimonial t) {
        return new TestimonialResponse(t.getId(), t.getClientId(), t.getAuthorName(), t.getPlanName(), t.getRating(),
                t.getContent(), t.isVisible(), t.getCreatedAt());
    }
}
