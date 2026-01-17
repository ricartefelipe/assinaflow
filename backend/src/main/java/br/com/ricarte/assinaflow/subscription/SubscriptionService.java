package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.common.exception.ConflictException;
import br.com.ricarte.assinaflow.common.exception.NotFoundException;
import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.subscription.dto.CreateSubscriptionRequest;
import br.com.ricarte.assinaflow.subscription.dto.SubscriptionResponse;
import br.com.ricarte.assinaflow.user.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final EnumSet<SubscriptionStatus> ACTIVE_STATUSES =
            EnumSet.of(SubscriptionStatus.ATIVA, SubscriptionStatus.CANCELAMENTO_AGENDADO);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final TimeProvider timeProvider;
    private final SubscriptionCache subscriptionCache;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            TimeProvider timeProvider,
            SubscriptionCache subscriptionCache
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.timeProvider = timeProvider;
        this.subscriptionCache = subscriptionCache;
    }

    @Transactional
    public SubscriptionResponse create(UUID userId, CreateSubscriptionRequest req) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("USER_NOT_FOUND", "Usuario nao encontrado.");
        }

        if (subscriptionRepository.existsByUserIdAndStatusIn(userId, ACTIVE_STATUSES)) {
            throw new ConflictException("SUBSCRIPTION_ALREADY_ACTIVE",
                    "Usuario ja possui uma assinatura ativa (ou cancelamento agendado)." );
        }

        LocalDate start = req.getDataInicio() != null ? req.getDataInicio() : timeProvider.todayUtc();
        LocalDate expiration = start.plusMonths(1);

        SubscriptionEntity s = new SubscriptionEntity();
        s.setUserId(userId);
        s.setPlan(req.getPlano());
        s.setStartDate(start);
        s.setExpirationDate(expiration);
        s.setStatus(SubscriptionStatus.ATIVA);
        s.setAutoRenew(true);
        s.setRenewalFailures(0);
        s.setNextRenewalAttemptAt(null);
        s.setRenewalInFlightUntil(null);

        try {
            s = subscriptionRepository.save(s);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("SUBSCRIPTION_ALREADY_ACTIVE",
                    "Usuario ja possui uma assinatura ativa (ou cancelamento agendado)." );
        } finally {
            subscriptionCache.evictActive(userId);
        }

        return toResponse(s);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = SubscriptionCache.ACTIVE_CACHE, key = "#userId")
    public SubscriptionResponse getActive(UUID userId) {
        SubscriptionEntity s = subscriptionRepository.findFirstByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
                .orElseThrow(() -> new NotFoundException("SUBSCRIPTION_NOT_FOUND", "Assinatura ativa nao encontrada."));
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> history(UUID userId) {
        return subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(SubscriptionService::toResponse)
                .toList();
    }

    @Transactional
    public SubscriptionResponse cancel(UUID userId) {
        SubscriptionEntity s = subscriptionRepository.findFirstByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
                .orElseThrow(() -> new NotFoundException("SUBSCRIPTION_NOT_FOUND", "Assinatura ativa nao encontrada."));

        if (s.getStatus() == SubscriptionStatus.CANCELAMENTO_AGENDADO) {
            return toResponse(s);
        }

        s.setStatus(SubscriptionStatus.CANCELAMENTO_AGENDADO);
        s.setAutoRenew(false);
        s.setCancelRequestedAt(timeProvider.now());

        s = subscriptionRepository.save(s);
        subscriptionCache.evictActive(userId);
        return toResponse(s);
    }

    static SubscriptionResponse toResponse(SubscriptionEntity s) {
        SubscriptionResponse r = new SubscriptionResponse();
        r.setId(s.getId());
        r.setUsuarioId(s.getUserId());
        r.setPlano(s.getPlan());
        r.setDataInicio(s.getStartDate());
        r.setDataExpiracao(s.getExpirationDate());
        r.setStatus(s.getStatus());
        r.setAutoRenew(s.isAutoRenew());
        r.setRenewalFailures(s.getRenewalFailures());
        r.setNextRenewalAttemptAt(s.getNextRenewalAttemptAt());
        return r;
    }
}
