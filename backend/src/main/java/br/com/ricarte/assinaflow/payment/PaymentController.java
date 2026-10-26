package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.common.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/payments")
public class PaymentController {
    private final JdbcTemplate jdbc;
    public PaymentController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping
    public List<Map<String,Object>> list(@PathVariable UUID userId) {
        return jdbc.queryForList("""
                SELECT id,subscription_id,amount_cents,currency,status,settled,created_at
                FROM payment_obligations WHERE user_id=? ORDER BY created_at DESC,id LIMIT 100
                """, userId);
    }
    @GetMapping("/{paymentId}")
    public Map<String,Object> get(@PathVariable UUID userId, @PathVariable UUID paymentId) {
        var rows = jdbc.queryForList("""
                SELECT id,subscription_id,amount_cents,currency,status,settled,created_at
                FROM payment_obligations WHERE user_id=? AND id=?
                """, userId,paymentId);
        if (rows.isEmpty()) throw new NotFoundException("PAYMENT_NOT_FOUND", "Pagamento nao encontrado.");
        return rows.getFirst();
    }
}
