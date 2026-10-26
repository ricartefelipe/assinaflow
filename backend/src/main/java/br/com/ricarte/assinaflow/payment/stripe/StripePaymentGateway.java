package br.com.ricarte.assinaflow.payment.stripe;

import br.com.ricarte.assinaflow.payment.*;
import br.com.ricarte.assinaflow.subscription.PaymentResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static br.com.ricarte.assinaflow.payment.stripe.StripeApi.*;

@Component
@ConditionalOnProperty(name="app.payments.gateway", havingValue="stripe")
public class StripePaymentGateway implements PaymentGateway {
    private final StripeApi api; private final StripeCustomers customers; private final JdbcTemplate jdbc;
    public StripePaymentGateway(StripeApi api,StripeCustomers customers,JdbcTemplate jdbc) { this.api=api;this.customers=customers;this.jdbc=jdbc; }
    @Override public boolean supportsRecovery() { return true; }
    @Override public void requireReady(UUID user) { customers.paymentMethod(user); }
    @Override public PaymentResult charge(PaymentChargeCommand command) { throw new UnsupportedOperationException("Use durable submit"); }
    @Override public ProviderPayment submit(PaymentChargeCommand c) {
        if (c.amountCents()==0) return credit(c);
        var method=customers.paymentMethod(c.userId());
        jdbc.update("INSERT INTO stripe_operations(obligation_id,customer_id,payment_method) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                c.obligationId(),method.get("customer_id"),method.get("payment_method"));
        var op=operation(c.obligationId());
        if ("NEW".equals(op.get("state"))) {
            if (((java.sql.Timestamp)op.get("created_at")).toInstant().plusSeconds(23*3600).isBefore(Instant.now())) throw new PaymentOutcomeUnknownException();
            if (jdbc.update("UPDATE stripe_operations SET state='CREATING' WHERE obligation_id=? AND state='NEW'",c.obligationId())!=1) return lookup(c,null);
            JsonNode intent=api.post("/payment_intents",c.idempotencyKey()+"-intent",Map.of(
                    "amount",Integer.toString(c.amountCents()),"currency","brl","customer",(String)op.get("customer_id"),
                    "payment_method_types[0]","card","capture_method","automatic","confirmation_method","automatic",
                    "metadata[obligation_id]",c.obligationId().toString(),"metadata[user_id]",c.userId().toString()));
            verify(c,intent,op);
            jdbc.update("UPDATE stripe_operations SET intent_id=?,state='READY' WHERE obligation_id=? AND intent_id IS NULL",text(intent,"id"),c.obligationId());
            op=operation(c.obligationId());
        }
        if (("READY".equals(op.get("state")) || "DECLINED".equals(op.get("state"))) && op.get("intent_id")!=null) {
            int changed=jdbc.update("UPDATE stripe_operations SET state='CONFIRMING',confirmations=confirmations+1 WHERE obligation_id=? AND state IN ('READY','DECLINED')",c.obligationId());
            if (changed==1) {
                jdbc.update("UPDATE stripe_operations SET payment_method=? WHERE obligation_id=? AND state='CONFIRMING'",method.get("payment_method"),c.obligationId());
                op=operation(c.obligationId());
                jdbc.update("INSERT INTO stripe_confirmations(obligation_id,number,request_key,payment_method) VALUES (?,?,?,?)",c.obligationId(),op.get("confirmations"),c.idempotencyKey()+"-confirm-"+op.get("confirmations"),op.get("payment_method"));
                JsonNode intent=api.post("/payment_intents/"+op.get("intent_id")+"/confirm",
                        c.idempotencyKey()+"-confirm-"+op.get("confirmations"),
                        Map.of("payment_method",(String)op.get("payment_method"),"off_session","true"));
                return receipt(c,intent,op);
            }
        }
        return lookup(c,(String)op.get("intent_id"));
    }
    @Override public ProviderPayment lookup(PaymentChargeCommand c,String reference) {
        if (c.amountCents()==0) return credit(c);
        var rows=jdbc.queryForList("SELECT * FROM stripe_operations WHERE obligation_id=?",c.obligationId());
        if (rows.isEmpty()) return missing(c);
        var op=rows.getFirst();String id=(String)op.get("intent_id");
        if (id==null) {
            String query=URLEncoder.encode("metadata['obligation_id']:'"+c.obligationId()+"'",StandardCharsets.UTF_8);
            JsonNode results=api.get("/payment_intents/search?limit=2&query="+query);
            if (results.path("has_more").asBoolean() || results.path("data").size()>1) throw new IllegalStateException("Multiple Stripe intents require review");
            if (results.path("data").isEmpty()) return missing(c);
            JsonNode found=results.path("data").get(0);verify(c,found,op);id=text(found,"id");
            jdbc.update("UPDATE stripe_operations SET intent_id=? WHERE obligation_id=? AND intent_id IS NULL",id,c.obligationId());
        }
        if (reference!=null && !reference.equals(id)) throw new IllegalStateException("Stripe reference mismatch");
        return receipt(c,api.get("/payment_intents/"+id),operation(c.obligationId()));
    }
    private void verify(PaymentChargeCommand c,JsonNode intent,Map<String,Object> op) {
        testObject(intent);
        if (!text(intent,"id").startsWith("pi_") || !c.obligationId().toString().equals(intent.path("metadata").path("obligation_id").asText())
                || !c.userId().toString().equals(intent.path("metadata").path("user_id").asText())
                || !op.get("customer_id").equals(text(intent,"customer")) || intent.path("amount").asInt(-1)!=c.amountCents()
                || !"brl".equals(text(intent,"currency")) || (op.get("intent_id")!=null && !op.get("intent_id").equals(text(intent,"id"))))
            throw new IllegalStateException("Stripe payment correlation mismatch");
    }
    private ProviderPayment receipt(PaymentChargeCommand c,JsonNode intent,Map<String,Object> op) {
        verify(c,intent,op);String status=text(intent,"status");
        boolean action=status.equals("requires_action") || "authentication_required".equals(intent.path("last_payment_error").path("code").asText());
        var result=switch(status) {
            case "succeeded" -> ProviderPayment.Status.APPROVED;
            case "canceled" -> ProviderPayment.Status.DECLINED;
            case "requires_payment_method" -> action || !intent.path("last_payment_error").isObject() ? ProviderPayment.Status.PENDING : ProviderPayment.Status.DECLINED;
            default -> ProviderPayment.Status.PENDING;
        };
        String state=action?"ACTION":result==ProviderPayment.Status.PENDING?"PENDING":result.name();
        jdbc.update("UPDATE stripe_operations SET state=? WHERE obligation_id=? AND state<>'APPROVED'",state,c.obligationId());
        return new ProviderPayment(result,text(intent,"id"),c.obligationId(),c.userId(),c.amountCents(),c.currency());
    }
    public Map<String,String> action(UUID user,UUID obligation) {
        var rows=jdbc.queryForList("SELECT o.* FROM stripe_operations o JOIN payment_obligations p ON p.id=o.obligation_id WHERE p.id=? AND p.user_id=? AND p.closed=false",obligation,user);
        if (rows.isEmpty()) throw new br.com.ricarte.assinaflow.common.exception.NotFoundException("PAYMENT_NOT_FOUND","Pagamento nao encontrado.");
        var op=rows.getFirst();String id=(String)op.get("intent_id");
        if (id==null) return Map.of("status","processing");
        JsonNode intent=api.get("/payment_intents/"+id);
        var c=jdbc.queryForMap("SELECT amount_cents FROM payment_obligations WHERE id=?",obligation);
        verify(new PaymentChargeCommand(user,(Integer)c.get("amount_cents"),"action","Authentication",obligation,"BRL"),intent,op);
        String status=text(intent,"status");
        if (!(status.equals("requires_action") || (status.equals("requires_payment_method") && "authentication_required".equals(intent.path("last_payment_error").path("code").asText())))) return Map.of("status",status);
        return Map.of("status","requires_action","clientSecret",text(intent,"client_secret"),"paymentMethod",(String)op.get("payment_method"));
    }
    private Map<String,Object> operation(UUID id) { return jdbc.queryForMap("SELECT * FROM stripe_operations WHERE obligation_id=?",id); }
    private ProviderPayment missing(PaymentChargeCommand c) { return new ProviderPayment(ProviderPayment.Status.NOT_FOUND,null,c.obligationId(),c.userId(),c.amountCents(),c.currency()); }
    private ProviderPayment credit(PaymentChargeCommand c) { return new ProviderPayment(ProviderPayment.Status.APPROVED,"credit-"+c.obligationId(),c.obligationId(),c.userId(),0,c.currency()); }
}
