package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.subscription.PaymentResult;
import br.com.ricarte.assinaflow.user.PaymentBehavior;
import br.com.ricarte.assinaflow.user.PaymentProfileEntity;
import br.com.ricarte.assinaflow.user.PaymentProfileRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.payments.gateway", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    private final PaymentProfileRepository paymentProfileRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public SimulatedPaymentGateway(PaymentProfileRepository paymentProfileRepository, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.paymentProfileRepository = paymentProfileRepository;
        this.jdbc = jdbc;
    }

    @Override
    public PaymentResult charge(PaymentChargeCommand command) {
        PaymentProfileEntity profile = paymentProfileRepository.findByUserIdForUpdate(command.userId()).orElse(null);

        if (profile == null) {
            return PaymentResult.approved();
        }

        PaymentBehavior behavior = Optional.ofNullable(profile.getBehavior()).orElse(PaymentBehavior.ALWAYS_APPROVE);

        return switch (behavior) {
            case ALWAYS_APPROVE -> PaymentResult.approved();
            case ALWAYS_DECLINE -> PaymentResult.declined("PAYMENT_DECLINED", "Pagamento recusado (simulado)");
            case FAIL_NEXT_N -> {
                int remaining = profile.getFailNextN();
                if (remaining > 0) {
                    profile.setFailNextN(remaining - 1);
                    yield PaymentResult.declined(
                            "PAYMENT_DECLINED",
                            "Pagamento recusado (simulado), failNextN restante=" + (remaining - 1)
                    );
                }
                yield PaymentResult.approved();
            }
        };
    }
    @Override
    public boolean supportsRecovery() { return true; }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ProviderPayment submit(PaymentChargeCommand command) {
        jdbc.update("""
                INSERT INTO simulated_provider_payments(idempotency_key,provider_reference,obligation_id,user_id,amount_cents,currency,status)
                VALUES (?,?,?,?,?,?,'PENDING') ON CONFLICT (idempotency_key) DO NOTHING
                """, command.idempotencyKey(), "sim-" + command.obligationId(), command.obligationId(),
                command.userId(), command.amountCents(), command.currency());
        ProviderPayment existing = read(command, true);
        if (!existing.matches(command)) throw new IllegalArgumentException("Provider payload mismatch");
        if (existing.status() == ProviderPayment.Status.APPROVED) return existing;
        PaymentResult result = charge(command);
        String status = result.isApproved() ? "APPROVED" : "DECLINED";
        jdbc.update("UPDATE simulated_provider_payments SET status=?, effects=? WHERE idempotency_key=?",
                status, result.isApproved() ? 1 : 0, command.idempotencyKey());
        return read(command, false);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ProviderPayment lookup(PaymentChargeCommand command, String reference) {
        var result = jdbc.query("SELECT * FROM simulated_provider_payments WHERE idempotency_key=?",
                (rs, n) -> receipt(rs), command.idempotencyKey());
        return result.isEmpty() ? new ProviderPayment(ProviderPayment.Status.NOT_FOUND, null,
                command.obligationId(), command.userId(), command.amountCents(), command.currency()) : result.getFirst();
    }

    private ProviderPayment read(PaymentChargeCommand command, boolean lock) {
        return jdbc.queryForObject("SELECT * FROM simulated_provider_payments WHERE idempotency_key=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> receipt(rs), command.idempotencyKey());
    }

    private ProviderPayment receipt(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProviderPayment(ProviderPayment.Status.valueOf(rs.getString("status")), rs.getString("provider_reference"),
                rs.getObject("obligation_id", java.util.UUID.class), rs.getObject("user_id", java.util.UUID.class),
                rs.getInt("amount_cents"), rs.getString("currency"));
    }

}
