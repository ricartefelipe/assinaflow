package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.common.exception.ConflictException;
import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.subscription.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentLedger {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TimeProvider time;
    private final SubscriptionRepository subscriptions;
    private final EntityManager entityManager;

    public PaymentLedger(JdbcTemplate jdbc, ObjectMapper mapper, TimeProvider time,
                         SubscriptionRepository subscriptions, EntityManager entityManager) {
        this.jdbc = jdbc; this.mapper = mapper; this.time = time;
        this.subscriptions = subscriptions; this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentObligation prepare(UUID user, int amount, String key) {
        lockUser(user);
        var existing = jdbc.query("SELECT * FROM payment_obligations WHERE business_key=? FOR UPDATE", this::row, key);
        if (!existing.isEmpty()) {
            var o = existing.getFirst();
            if (!o.userId().equals(user) || o.amountCents() != amount || !"BRL".equals(o.currency()))
                throw new ConflictException("PAYMENT_IDENTITY_MISMATCH", "Obrigacao possui valor ou titular diferente.");
            return o;
        }
        assertNoPending(user);
        String[] parts = key.split("\\|");
        PaymentContext context;
        UUID subscription = null;
        if (parts[0].equals("subscribe") && parts.length == 5) {
            if (!parts[1].equals(user.toString())) throw new IllegalArgumentException("Invalid payment owner");
            if (subscriptions.existsByUserIdAndStatusIn(user, java.util.List.of(SubscriptionStatus.ATIVA, SubscriptionStatus.CANCELAMENTO_AGENDADO)))
                throw new ConflictException("SUBSCRIPTION_ALREADY_ACTIVE", "Assinatura ja ativa.");
            context = new PaymentContext("SUBSCRIBE", null, Plan.valueOf(parts[2]).name(), LocalDate.parse(parts[3]), null, Long.parseLong(parts[4]), 0, Plan.valueOf(parts[2]).getPriceCents());
        } else {
            subscription = UUID.fromString(parts[1]);
            var s = subscriptions.findByIdForUpdate(subscription).orElseThrow();
            if (!s.getUserId().equals(user)) throw new IllegalArgumentException("Invalid payment owner");
            String kind = switch (parts[0]) {
                case "renewal" -> "RENEWAL";
                case "reactivate" -> "REACTIVATE";
                case "change-plan" -> "CHANGE_PLAN";
                default -> throw new IllegalArgumentException("Unsupported payment operation");
            };
            String plan = kind.equals("CHANGE_PLAN") ? Plan.valueOf(parts[2]).name() : s.getPlan().name();
            LocalDate start = kind.equals("RENEWAL") ? s.getExpirationDate() : LocalDate.parse(parts[3]);
            context = new PaymentContext(kind, subscription, plan, start, s.getExpirationDate(), s.getVersion(), s.getRenewalCreditCents(), s.getPlan().getPriceCents());
            if (kind.equals("RENEWAL") && (!LocalDate.parse(parts[2]).equals(s.getExpirationDate())
                    || s.getStatus() != SubscriptionStatus.ATIVA || !s.isAutoRenew()))
                throw new ConflictException("PAYMENT_CYCLE_CANCELED", "Ciclo nao esta autorizado para renovacao.");
        }
        if (context.kind().equals("SUBSCRIBE") && amount != Plan.valueOf(context.plan()).getPriceCents())
            throw new IllegalArgumentException("Invalid subscription price");
        if ((context.kind().equals("RENEWAL") || context.kind().equals("REACTIVATE"))
                && amount != new ProrationService().renewalAmountCents(Plan.valueOf(context.plan()), context.creditCents()))
            throw new IllegalArgumentException("Invalid cycle price");
        return insert(user, amount, key, context);
    }

    /** The caller already owns the subscription lock; persist intent with the outbox reservation. */
    @Transactional
    public PaymentObligation reserveRenewal(SubscriptionEntity subscription, int amount) {
        String key = "renewal|" + subscription.getId() + "|" + subscription.getExpirationDate();
        var context = new PaymentContext("RENEWAL", subscription.getId(), subscription.getPlan().name(),
                subscription.getExpirationDate(), subscription.getExpirationDate(), subscription.getVersion(),
                subscription.getRenewalCreditCents(), subscription.getPlan().getPriceCents());
        return insert(subscription.getUserId(), amount, key, context);
    }

    private PaymentObligation insert(UUID user, int amount, String key, PaymentContext context) {
        UUID id = UUID.randomUUID();
        Instant now = time.now();
        jdbc.update("""
                INSERT INTO payment_obligations(id,business_key,user_id,subscription_id,amount_cents,currency,idempotency_key,
                  context,kind,cycle_date,status,created_at,retention_until,next_attempt_at,updated_at)
                VALUES (?,?,?,?,?,'BRL',?,?::jsonb,?,?,'PREPARED',?,?,?,?) ON CONFLICT DO NOTHING
                """, id, key, user, context.subscriptionId(), amount, "obligation-" + id, json(context), context.kind(), context.cycle(),
                ts(now), ts(now.plus(Duration.ofHours(48))), ts(now), ts(now));
        var rows = jdbc.query("SELECT * FROM payment_obligations WHERE business_key=?", this::row, key);
        if (rows.isEmpty()) throw new ConflictException("PAYMENT_PENDING", "Outra obrigacao exige reconciliacao.");
        var existing = rows.getFirst();
        if (existing.amountCents() != amount || !existing.userId().equals(user))
            throw new ConflictException("PAYMENT_IDENTITY_MISMATCH", "Obrigacao possui valor ou titular diferente.");
        return existing;
    }

    /** Call inside the transaction that changes subscription state. Lock order: user, subscription, obligation. */
    @Transactional
    public void lockUser(UUID user) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", UUID.class, user);
    }

    @Transactional
    public void assertNoPending(UUID user) {
        lockUser(user);
        if (jdbc.queryForObject("SELECT count(*) FROM payment_obligations WHERE user_id=? AND closed=false", Long.class, user) > 0)
            throw new ConflictException("PAYMENT_PENDING", "Pagamento pendente de reconciliacao ou revisao.");
    }

    /** A definitive initial refusal is retried only by a new explicit user request, never by recovery. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentObligation reauthorizeDeclined(UUID id) {
        var initial = get(id);
        lockUser(initial.userId());
        var o = jdbc.queryForObject("SELECT * FROM payment_obligations WHERE id=? FOR UPDATE", this::row, id);
        if (!o.closed() || !o.status().equals("DECLINED") || o.context().kind().equals("RENEWAL")) return o;
        if (!o.retentionUntil().isAfter(time.now()))
            throw new ConflictException("PAYMENT_RETRY_WINDOW_EXPIRED", "Prazo para repetir esta obrigacao expirou.");
        assertNoPending(o.userId());
        jdbc.update("UPDATE payment_obligations SET closed=false,next_attempt_at=?,updated_at=? WHERE id=?",
                ts(time.now()), ts(time.now()), id);
        return get(id);
    }

    @Transactional
    public void resumeCanceledRenewal(UUID user, UUID subscription, LocalDate cycle) {
        lockUser(user);
        var rows = jdbc.query("SELECT * FROM payment_obligations WHERE business_key=? AND user_id=? FOR UPDATE",
                this::row, "renewal|" + subscription + "|" + cycle, user);
        if (rows.isEmpty() || !rows.getFirst().status().equals("CANCELED")) return;
        var o = rows.getFirst();
        if (!o.retentionUntil().isAfter(time.now()))
            throw new ConflictException("PAYMENT_RETRY_WINDOW_EXPIRED", "Retomada exige revisao do ciclo encerrado.");
        assertNoPending(user);
        jdbc.update("""
                UPDATE payment_obligations SET status='PREPARED',closed=false,settled=false,
                  next_attempt_at=?,updated_at=? WHERE id=?
                """, ts(time.now()), ts(time.now()), o.id());
    }

    public boolean authorizedRenewal(UUID subscription, UUID user, LocalDate cycle, int amount) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM payment_obligations WHERE business_key=? AND kind='RENEWAL'
                  AND subscription_id=? AND user_id=? AND amount_cents=? AND currency='BRL'
                """, Integer.class, "renewal|" + subscription + "|" + cycle, subscription, user, amount) == 1;
    }

    public PaymentObligation get(UUID id) {
        return jdbc.queryForObject("SELECT * FROM payment_obligations WHERE id=?", this::row, id);
    }

    public List<UUID> due(int limit) {
        return jdbc.query("""
                SELECT id FROM payment_obligations WHERE closed=false AND status <> 'REVIEW'
                  AND next_attempt_at<=? AND (lease_until IS NULL OR lease_until<=?)
                ORDER BY next_attempt_at,id LIMIT ?
                """, (rs, n) -> rs.getObject(1, UUID.class), ts(time.now()), ts(time.now()), Math.min(limit, 200));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentObligation claim(UUID id) { return claimInternal(id, false); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentObligation claimLookup(UUID id) { return claimInternal(id, true); }

    private PaymentObligation claimInternal(UUID id, boolean lookupOnly) {
        var initial = get(id);
        lockUser(initial.userId());
        SubscriptionEntity subscription = initial.subscriptionId() == null ? null
                : subscriptions.findByIdForUpdate(initial.subscriptionId()).orElseThrow();
        var o = jdbc.queryForObject("SELECT * FROM payment_obligations WHERE id=? FOR UPDATE", this::row, id);
        Instant now = time.now();
        if (o.closed() || o.status().equals("REVIEW") || o.nextAttemptAt().isAfter(now)
                || (o.leaseUntil() != null && o.leaseUntil().isAfter(now))) return null;
        boolean send = o.status().equals("PREPARED") || (o.status().equals("DECLINED") && o.settled());
        if (send && lookupOnly) return null;
        if (send && o.context().kind().equals("RENEWAL") && (subscription.getStatus() != SubscriptionStatus.ATIVA
                || !subscription.isAutoRenew() || !subscription.getExpirationDate().equals(o.context().cycle()))) {
            jdbc.update("UPDATE payment_obligations SET status='CANCELED',closed=true,settled=true,owner=null,lease_until=null WHERE id=?", id);
            return null;
        }
        if (send && !o.retentionUntil().isAfter(now)) { review(id, "IDEMPOTENCY_WINDOW_EXPIRED"); return null; }
        String operation = send ? "CHARGE" : (!o.settled() && (o.status().equals("APPROVED") || o.status().equals("DECLINED"))) ? "APPLY" : "QUERY";
        UUID owner = UUID.randomUUID();
        int sequence = o.executions() + 1;
        jdbc.update("""
                UPDATE payment_obligations SET owner=?,lease_until=?,execution_count=?,settled=false,
                  status=CASE WHEN ?='APPLY' THEN status ELSE 'IN_FLIGHT' END,updated_at=? WHERE id=?
                """, owner, ts(now.plusSeconds(120)), sequence, operation, ts(now), id);
        jdbc.update("INSERT INTO payment_executions(id,obligation_id,sequence,operation,owner,started_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, sequence, operation, owner, ts(now));
        return get(id);
    }

    public String operation(PaymentObligation o) {
        return jdbc.queryForObject("SELECT operation FROM payment_executions WHERE obligation_id=? AND sequence=?", String.class, o.id(), o.executions());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentObligation record(PaymentObligation claim, ProviderPayment receipt) {
        var current = jdbc.queryForObject("SELECT * FROM payment_obligations WHERE id=? FOR UPDATE", this::row, claim.id());
        if (!claim.owner().equals(current.owner())) return null;
        Instant now = time.now();
        String outcome;
        if (receipt == null || receipt.status() == ProviderPayment.Status.PENDING || receipt.status() == ProviderPayment.Status.NOT_FOUND) {
            int lookups = current.lookups() + 1;
            boolean review = lookups >= 6 || !current.retentionUntil().isAfter(now);
            outcome = review ? "REVIEW" : "UNKNOWN";
            jdbc.update("""
                    UPDATE payment_obligations SET status=?,lookup_count=?,owner=null,lease_until=null,
                      next_attempt_at=?,review_reason=?,updated_at=? WHERE id=?
                    """, outcome, lookups, ts(now.plusSeconds(Math.min(900, 15L << Math.min(lookups - 1, 6)))),
                    review ? "INCONCLUSIVE_PROVIDER_RESULT" : null, ts(now), claim.id());
        } else if (!receipt.matches(claim.command()) || (current.providerReference() != null && !current.providerReference().equals(receipt.reference()))) {
            outcome = "REVIEW";
            review(claim.id(), "PROVIDER_CORRELATION_MISMATCH");
        } else {
            outcome = receipt.status().name();
            jdbc.update("UPDATE payment_obligations SET status=?,provider_reference=?,updated_at=? WHERE id=?",
                    outcome, receipt.reference(), ts(now), claim.id());
        }
        jdbc.update("UPDATE payment_executions SET result=?,finished_at=? WHERE obligation_id=? AND sequence=? AND owner=?",
                outcome, ts(now), claim.id(), claim.executions(), claim.owner());
        return get(claim.id());
    }

    @Transactional
    public PaymentObligation lockForSettlement(PaymentObligation claim) {
        var o = jdbc.queryForObject("SELECT * FROM payment_obligations WHERE id=? FOR UPDATE", this::row, claim.id());
        return o.settled() || o.owner() == null || !o.owner().equals(claim.owner()) ? null : o;
    }

    @Transactional
    public void applied(PaymentObligation claim, UUID subscriptionId, boolean approved, int failures) {
        entityManager.flush();
        boolean closed = approved || !claim.context().kind().equals("RENEWAL") || failures >= 3;
        Instant next = time.now().plus(failures == 1 ? Duration.ofMinutes(15) : Duration.ofMinutes(60));
        int updated = jdbc.update("""
                UPDATE payment_obligations SET subscription_id=?,settled=true,closed=?,failure_count=?,owner=null,
                  lease_until=null,next_attempt_at=?,updated_at=? WHERE id=? AND owner=? AND settled=false
                """, subscriptionId, closed, failures, ts(next), ts(time.now()), claim.id(), claim.owner());
        if (updated != 1) throw new ConflictException("PAYMENT_LEASE_LOST", "Resultado pertence a outro worker.");
        jdbc.update("UPDATE payment_executions SET result=?,finished_at=? WHERE obligation_id=? AND sequence=?",
                approved ? "APPROVED" : "DECLINED", ts(time.now()), claim.id(), claim.executions());
    }

    @Transactional
    public void review(UUID id, String reason) {
        jdbc.update("UPDATE payment_obligations SET status='REVIEW',review_reason=?,owner=null,lease_until=null,updated_at=? WHERE id=?",
                reason, ts(time.now()), id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void wakeForProvider(UUID id) {
        jdbc.update("""
                UPDATE payment_obligations SET next_attempt_at=?,
                  status=CASE WHEN status='REVIEW' AND review_reason='INCONCLUSIVE_PROVIDER_RESULT' THEN 'UNKNOWN' ELSE status END,
                  lookup_count=CASE WHEN status='REVIEW' AND review_reason='INCONCLUSIVE_PROVIDER_RESULT' THEN 0 ELSE lookup_count END
                WHERE id=? AND closed=false AND status IN ('IN_FLIGHT','UNKNOWN','APPROVED','REVIEW')
                """,ts(time.now()),id);
    }

    private PaymentObligation row(ResultSet rs, int number) throws SQLException {
        try {
            Timestamp lease = rs.getTimestamp("lease_until");
            return new PaymentObligation(rs.getObject("id", UUID.class), rs.getString("business_key"), rs.getObject("user_id", UUID.class),
                    rs.getObject("subscription_id", UUID.class), rs.getInt("amount_cents"), rs.getString("currency"),
                    rs.getString("idempotency_key"), rs.getString("provider_reference"), mapper.readValue(rs.getString("context"), PaymentContext.class), rs.getString("status"),
                    rs.getBoolean("closed"), rs.getBoolean("settled"), rs.getInt("failure_count"), rs.getInt("execution_count"), rs.getInt("lookup_count"),
                    rs.getObject("owner", UUID.class), lease == null ? null : lease.toInstant(), rs.getTimestamp("next_attempt_at").toInstant(),
                    rs.getTimestamp("retention_until").toInstant());
        } catch (java.io.IOException ex) { throw new IllegalStateException("Invalid persisted payment context", ex); }
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception ex) { throw new IllegalArgumentException(ex); } }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
}
