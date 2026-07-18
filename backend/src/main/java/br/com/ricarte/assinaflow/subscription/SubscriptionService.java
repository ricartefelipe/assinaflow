package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.common.exception.BadRequestException;
import br.com.ricarte.assinaflow.common.exception.ConflictException;
import br.com.ricarte.assinaflow.common.exception.NotFoundException;
import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.notification.NotificationService;
import br.com.ricarte.assinaflow.subscription.dto.ChangePlanRequest;
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
import java.util.Map;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final EnumSet<SubscriptionStatus> ACTIVE_STATUSES =
            EnumSet.of(SubscriptionStatus.ATIVA, SubscriptionStatus.CANCELAMENTO_AGENDADO);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final TimeProvider timeProvider;
    private final SubscriptionCache subscriptionCache;
    private final PaymentService paymentService;
    private final ProrationService prorationService;
    private final NotificationService notificationService;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            TimeProvider timeProvider,
            SubscriptionCache subscriptionCache,
            PaymentService paymentService,
            ProrationService prorationService,
            NotificationService notificationService
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.timeProvider = timeProvider;
        this.subscriptionCache = subscriptionCache;
        this.paymentService = paymentService;
        this.prorationService = prorationService;
        this.notificationService = notificationService;
    }

    @Transactional
    public SubscriptionResponse create(UUID userId, CreateSubscriptionRequest req) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("USER_NOT_FOUND", "Usuario nao encontrado.");
        }

        if (subscriptionRepository.existsByUserIdAndStatusIn(userId, ACTIVE_STATUSES)) {
            throw new ConflictException("SUBSCRIPTION_ALREADY_ACTIVE",
                    "Usuario ja possui uma assinatura ativa (ou cancelamento agendado).");
        }

        LocalDate start = req.getDataInicio() != null ? req.getDataInicio() : timeProvider.todayUtc();
        LocalDate expiration = start.plusMonths(1);
        int amountCents = req.getPlano().getPriceCents();

        PaymentResult payment = paymentService.charge(
                userId,
                amountCents,
                "subscribe|" + userId + "|" + req.getPlano() + "|" + start,
                "Assinatura " + req.getPlano()
        );
        if (!payment.isApproved()) {
            throw new BadRequestException(
                    payment.errorCode() != null ? payment.errorCode() : "PAYMENT_DECLINED",
                    payment.errorMessage() != null ? payment.errorMessage() : "Pagamento recusado."
            );
        }

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
                    "Usuario ja possui uma assinatura ativa (ou cancelamento agendado).");
        } finally {
            subscriptionCache.evictActive(userId);
        }

        notificationService.enqueue(
                NotificationService.SUBSCRIPTION_CREATED,
                userId,
                s.getId(),
                "Assinatura ativada",
                "Sua assinatura " + s.getPlan() + " foi ativada.",
                Map.of("plano", s.getPlan().name()),
                "notification|created|" + s.getId()
        );

        return toResponse(s);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = SubscriptionCache.ACTIVE_CACHE, key = "#userId", unless = "#result == null")
    public SubscriptionResponse getActive(UUID userId) {
        requireUser(userId);
        return subscriptionRepository.findFirstByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
                .or(() -> subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                        userId, SubscriptionStatus.SUSPENSA))
                .map(SubscriptionService::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getById(UUID userId, UUID subscriptionId) {
        requireUser(userId);
        SubscriptionEntity s = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NotFoundException("SUBSCRIPTION_NOT_FOUND", "Assinatura nao encontrada."));
        if (!userId.equals(s.getUserId())) {
            throw new NotFoundException("SUBSCRIPTION_NOT_FOUND", "Assinatura nao encontrada.");
        }
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> history(UUID userId) {
        requireUser(userId);
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

    @Transactional
    public SubscriptionResponse resume(UUID userId) {
        requireUser(userId);
        SubscriptionEntity s = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, SubscriptionStatus.CANCELAMENTO_AGENDADO)
                .orElseThrow(() -> new NotFoundException(
                        "SUBSCRIPTION_NOT_FOUND",
                        "Nao ha cancelamento agendado para retomar."));

        s.setStatus(SubscriptionStatus.ATIVA);
        s.setAutoRenew(true);
        s.setCancelRequestedAt(null);

        s = subscriptionRepository.save(s);
        subscriptionCache.evictActive(userId);
        return toResponse(s);
    }

    @Transactional
    public SubscriptionResponse reactivate(UUID userId) {
        requireUser(userId);

        if (subscriptionRepository.existsByUserIdAndStatusIn(userId, ACTIVE_STATUSES)) {
            throw new ConflictException(
                    "SUBSCRIPTION_ALREADY_ACTIVE",
                    "Usuario ja possui uma assinatura ativa (ou cancelamento agendado).");
        }

        SubscriptionEntity s = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, SubscriptionStatus.SUSPENSA)
                .orElseThrow(() -> new NotFoundException(
                        "SUBSCRIPTION_NOT_FOUND",
                        "Nao ha assinatura suspensa para reativar."));

        LocalDate today = timeProvider.todayUtc();
        if (!s.getExpirationDate().isAfter(today)) {
            s.setStartDate(today);
            s.setExpirationDate(today.plusMonths(1));
        }

        s.setStatus(SubscriptionStatus.ATIVA);
        s.setAutoRenew(true);
        s.setRenewalFailures(0);
        s.setNextRenewalAttemptAt(null);
        s.setRenewalInFlightUntil(null);
        s.setSuspendedAt(null);

        s = subscriptionRepository.save(s);
        subscriptionCache.evictActive(userId);
        return toResponse(s);
    }

    @Transactional
    public SubscriptionResponse changePlan(UUID userId, ChangePlanRequest req) {
        requireUser(userId);

        SubscriptionEntity s = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, SubscriptionStatus.ATIVA)
                .orElseThrow(() -> new NotFoundException(
                        "SUBSCRIPTION_NOT_FOUND",
                        "Assinatura ativa nao encontrada."));

        if (s.getPlan() == req.getPlano()) {
            throw new BadRequestException("PLAN_UNCHANGED", "A assinatura ja esta neste plano.");
        }

        LocalDate today = timeProvider.todayUtc();
        int delta = prorationService.proratedDeltaCents(
                s.getPlan(), req.getPlano(), s.getStartDate(), s.getExpirationDate(), today
        );

        if (delta > 0) {
            PaymentResult payment = paymentService.charge(
                    userId,
                    delta,
                    "change-plan|" + s.getId() + "|" + req.getPlano() + "|" + today,
                    "Upgrade para " + req.getPlano()
            );
            if (!payment.isApproved()) {
                throw new BadRequestException(
                        payment.errorCode() != null ? payment.errorCode() : "PAYMENT_DECLINED",
                        payment.errorMessage() != null ? payment.errorMessage() : "Pagamento recusado."
                );
            }
        } else if (delta < 0) {
            s.setRenewalCreditCents(s.getRenewalCreditCents() + (-delta));
        }

        s.setPlan(req.getPlano());
        s = subscriptionRepository.save(s);
        subscriptionCache.evictActive(userId);

        notificationService.enqueue(
                NotificationService.PLAN_CHANGED,
                userId,
                s.getId(),
                "Plano alterado",
                "Sua assinatura agora e " + s.getPlan() + ".",
                Map.of("plano", s.getPlan().name(), "deltaCentavos", String.valueOf(delta)),
                "notification|plan-changed|" + s.getId() + "|" + s.getPlan() + "|" + today
        );

        return toResponse(s);
    }

    private void requireUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("USER_NOT_FOUND", "Usuario nao encontrado.");
        }
    }

    public static SubscriptionResponse toResponse(SubscriptionEntity s) {
        SubscriptionResponse r = new SubscriptionResponse();
        r.setId(s.getId());
        r.setUsuarioId(s.getUserId());
        r.setPlano(s.getPlan());
        r.setDataInicio(s.getStartDate());
        r.setDataExpiracao(s.getExpirationDate());
        r.setStatus(s.getStatus());
        r.setAutoRenew(s.isAutoRenew());
        r.setRenewalFailures(s.getRenewalFailures());
        r.setCreditoRenovacaoCentavos(s.getRenewalCreditCents());
        r.setNextRenewalAttemptAt(s.getNextRenewalAttemptAt());
        return r;
    }
}
