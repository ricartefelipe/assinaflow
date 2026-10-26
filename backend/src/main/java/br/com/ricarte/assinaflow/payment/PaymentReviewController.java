package br.com.ricarte.assinaflow.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/payments")
public class PaymentReviewController {
    private final JdbcTemplate jdbc;
    public PaymentReviewController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping("/review")
    public List<Map<String,Object>> review() {
        return jdbc.queryForList("""
                SELECT id,user_id,subscription_id,amount_cents,currency,status,provider_reference,review_reason,created_at
                FROM payment_obligations WHERE status='REVIEW' ORDER BY created_at,id LIMIT 100
                """);
    }
}
