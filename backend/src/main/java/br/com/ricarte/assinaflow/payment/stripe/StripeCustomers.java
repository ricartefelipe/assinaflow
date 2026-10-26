package br.com.ricarte.assinaflow.payment.stripe;

import br.com.ricarte.assinaflow.common.exception.ConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static br.com.ricarte.assinaflow.payment.stripe.StripeApi.*;

@Service
@ConditionalOnProperty(name="app.payments.gateway", havingValue="stripe")
public class StripeCustomers {
    private final StripeApi api; private final JdbcTemplate jdbc; private final String returnUrl;
    public StripeCustomers(StripeApi api,JdbcTemplate jdbc,@Value("${STRIPE_RETURN_URL:http://localhost:5173/conta}") String returnUrl) {
        this.api=api;this.jdbc=jdbc;this.returnUrl=returnUrl;
        URI u=URI.create(returnUrl);
        if (!("https".equals(u.getScheme()) || ("http".equals(u.getScheme()) && ("localhost".equals(u.getHost()) || "127.0.0.1".equals(u.getHost())))) || u.getQuery()!=null || u.getFragment()!=null)
            throw new IllegalArgumentException("Invalid Stripe return URL");
    }
    public String setup(UUID user) {
        jdbc.update("INSERT INTO stripe_customers(user_id) VALUES (?) ON CONFLICT DO NOTHING",user);
        var row=jdbc.queryForMap("SELECT * FROM stripe_customers WHERE user_id=?",user);
        String customer=(String)row.get("customer_id");
        if (customer==null) {
            if (((java.sql.Timestamp)row.get("created_at")).toInstant().plusSeconds(23*3600).isBefore(Instant.now()))
                throw new ConflictException("STRIPE_CUSTOMER_REVIEW","Cadastro de cliente exige verificacao operacional.");
            JsonNode created=api.post("/customers","customer-"+user,Map.of("metadata[user_id]",user.toString()));
            testObject(created);customer=text(created,"id");
            if (!customer.startsWith("cus_")) throw new IllegalStateException("Invalid Stripe customer");
            jdbc.update("UPDATE stripe_customers SET customer_id=? WHERE user_id=? AND customer_id IS NULL",customer,user);
            if (!customer.equals(jdbc.queryForObject("SELECT customer_id FROM stripe_customers WHERE user_id=?",String.class,user))) throw new IllegalStateException("Customer conflict");
        }
        JsonNode session=api.post("/checkout/sessions","setup-"+UUID.randomUUID(),Map.of("mode","setup","customer",customer,
                "payment_method_types[0]","card","setup_intent_data[metadata][user_id]",user.toString(),
                "success_url",returnUrl+"?stripe_setup=complete","cancel_url",returnUrl+"?stripe_setup=canceled"));
        testObject(session);
        if (!customer.equals(text(session,"customer")) || !text(session,"id").startsWith("cs_test_")) throw new IllegalStateException("Invalid setup session");
        URI url=URI.create(text(session,"url"));
        if (!"https".equals(url.getScheme()) || !"checkout.stripe.com".equals(url.getHost())) throw new IllegalStateException("Invalid hosted URL");
        jdbc.update("UPDATE stripe_customers SET setup_session=? WHERE user_id=?",text(session,"id"),user);
        return url.toString();
    }
    public boolean refresh(UUID user) {
        var rows=jdbc.queryForList("SELECT * FROM stripe_customers WHERE user_id=?",user);
        if (rows.isEmpty()) return false;
        var row=rows.getFirst();String session=(String)row.get("setup_session");
        if (session!=null) {
            JsonNode result=api.get("/checkout/sessions/"+session);testObject(result);
            if (!row.get("customer_id").equals(text(result,"customer")) || !session.equals(text(result,"id"))) throw new IllegalStateException("Setup correlation mismatch");
            if ("complete".equals(text(result,"status"))) {
                String id=text(result,"setup_intent");if (!id.startsWith("seti_")) throw new IllegalStateException("Invalid setup intent");
                JsonNode intent=api.get("/setup_intents/"+id);testObject(intent);
                if (!row.get("customer_id").equals(text(intent,"customer")) || !"succeeded".equals(text(intent,"status"))) return false;
                String method=text(intent,"payment_method");if (!method.startsWith("pm_")) throw new IllegalStateException("Invalid payment method");
                JsonNode pm=api.get("/payment_methods/"+method);testObject(pm);
                if (!row.get("customer_id").equals(text(pm,"customer"))) throw new IllegalStateException("Payment method owner mismatch");
                jdbc.update("UPDATE stripe_customers SET payment_method=?,setup_session=NULL WHERE user_id=? AND setup_session=?",method,user,session);
            }
        }
        return jdbc.queryForObject("SELECT payment_method IS NOT NULL FROM stripe_customers WHERE user_id=?",Boolean.class,user);
    }
    public Map<String,Object> paymentMethod(UUID user) {
        var rows=jdbc.queryForList("SELECT customer_id,payment_method FROM stripe_customers WHERE user_id=? AND payment_method IS NOT NULL",user);
        if (rows.isEmpty()) throw new ConflictException("STRIPE_SETUP_REQUIRED","Cadastre o meio de pagamento na pagina segura do provedor.");
        return rows.getFirst();
    }
}
