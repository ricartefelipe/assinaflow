package br.com.ricarte.assinaflow.admin;

import br.com.ricarte.assinaflow.common.exception.NotFoundException;
import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.outbox.OutboxEventEntity;
import br.com.ricarte.assinaflow.outbox.OutboxRepository;
import br.com.ricarte.assinaflow.outbox.OutboxStatus;
import br.com.ricarte.assinaflow.subscription.SubscriptionRepository;
import br.com.ricarte.assinaflow.subscription.SubscriptionService;
import br.com.ricarte.assinaflow.subscription.dto.SubscriptionResponse;
import br.com.ricarte.assinaflow.user.UserRepository;
import br.com.ricarte.assinaflow.user.UserService;
import br.com.ricarte.assinaflow.user.dto.PaymentProfileRequest;
import br.com.ricarte.assinaflow.user.dto.UserResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OutboxRepository outboxRepository;
    private final UserService userService;
    private final TimeProvider timeProvider;

    public AdminService(
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            OutboxRepository outboxRepository,
            UserService userService,
            TimeProvider timeProvider
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.outboxRepository = outboxRepository;
        this.userService = userService;
        this.timeProvider = timeProvider;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(int limit) {
        return userRepository.findAll(PageRequest.of(0, Math.min(Math.max(limit, 1), 200)))
                .stream()
                .map(u -> userService.get(u.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listSubscriptions(int limit) {
        return subscriptionRepository.findAll(PageRequest.of(0, Math.min(Math.max(limit, 1), 200)))
                .stream()
                .map(SubscriptionService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OutboxEventEntity> listOutbox(OutboxStatus status, int limit) {
        return outboxRepository.findByStatusOrderByCreatedAtDesc(
                status,
                PageRequest.of(0, Math.min(Math.max(limit, 1), 200))
        );
    }

    @Transactional
    public OutboxEventEntity requeueOutbox(UUID id) {
        OutboxEventEntity e = outboxRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("OUTBOX_NOT_FOUND", "Evento de outbox nao encontrado."));
        Instant now = timeProvider.now();
        e.setStatus(OutboxStatus.PENDING);
        e.setPublishAttempts(0);
        e.setNextAttemptAt(now);
        e.setDeadAt(null);
        e.setLastError(null);
        e.setSentAt(null);
        return outboxRepository.save(e);
    }

    @Transactional
    public UserResponse updatePaymentProfile(UUID userId, PaymentProfileRequest req) {
        return userService.updatePaymentProfile(userId, req);
    }
}
