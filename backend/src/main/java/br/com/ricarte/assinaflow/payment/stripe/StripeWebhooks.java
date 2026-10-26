package br.com.ricarte.assinaflow.payment.stripe;

import br.com.ricarte.assinaflow.subscription.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@ConditionalOnProperty(name="app.payments.gateway",havingValue="stripe")
public class StripeWebhooks {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final String secret;
    private final PaymentService payments;private final StripeCustomers customers;private final boolean enabled;
    public StripeWebhooks(JdbcTemplate jdbc,ObjectMapper mapper,PaymentService payments,StripeCustomers customers,
                          @Value("${STRIPE_WEBHOOK_SECRET:}") String secret,
                          @Value("${app.payments.recovery.enabled:true}") boolean enabled) {
        if (!secret.startsWith("whsec_")) throw new IllegalStateException("Stripe webhook secret required");
        this.jdbc=jdbc;this.mapper=mapper;this.secret=secret;this.payments=payments;this.customers=customers;this.enabled=enabled;
    }
    public void receive(byte[] body,String signature) {
        try {
            if (body.length>262144 || signature==null || signature.length()>4096) throw new IllegalArgumentException();
            long timestamp=-1;List<String> signatures=new ArrayList<>();
            for(String item:signature.split(",")) {
                String[] pair=item.trim().split("=",2);
                if(pair.length==2 && pair[0].equals("t")) timestamp=Long.parseLong(pair[1]);
                if(pair.length==2 && pair[0].equals("v1")) signatures.add(pair[1]);
            }
            long now=Instant.now().getEpochSecond();
            if (timestamp<now-300 || timestamp>now+300) throw new IllegalArgumentException();
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));byte[] expected=mac.doFinal(body);
            if (signatures.stream().noneMatch(s -> {try{return MessageDigest.isEqual(expected,HexFormat.of().parseHex(s));}catch(Exception ex){return false;}})) throw new IllegalArgumentException();
            var event=mapper.readTree(body);StripeApi.testObject(event);
            String id=event.path("id").asText();if(!id.startsWith("evt_") || id.length()>100) throw new IllegalArgumentException();
            var object=event.path("data").path("object");UUID obligation=null;String customer=null;
            if (event.path("type").asText().startsWith("payment_intent.")) {
                String raw=object.path("metadata").path("obligation_id").asText();
                if(!raw.isBlank()) {
                    UUID candidate=UUID.fromString(raw);
                    if(jdbc.queryForObject("SELECT count(*) FROM payment_obligations WHERE id=?",Integer.class,candidate)==1) obligation=candidate;
                }
            } else if (event.path("type").asText().equals("checkout.session.completed")) customer=object.path("customer").asText();
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            jdbc.update("INSERT INTO stripe_webhook_inbox(event_id,payload_hash,obligation_id,customer_id,state) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",id,hash,obligation,customer,obligation==null && customer==null?"DONE":"PENDING");
            if (!hash.equals(jdbc.queryForObject("SELECT payload_hash FROM stripe_webhook_inbox WHERE event_id=?",String.class,id))) throw new IllegalArgumentException();
        } catch (org.springframework.dao.DataAccessException ex) {throw ex;}
        catch (Exception ex) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Stripe notification");}
    }
    @Scheduled(fixedDelayString="${app.payments.recovery.delay-ms:15000}")
    public void scheduled() { if(enabled) process(); }
    public void process() {
        var rows=jdbc.queryForList("SELECT event_id FROM stripe_webhook_inbox WHERE state IN ('PENDING','PROCESSING') AND available_at<=now() ORDER BY received_at LIMIT 100");
        for(var row:rows) {
            String id=(String)row.get("event_id");
            if(jdbc.update("UPDATE stripe_webhook_inbox SET state='PROCESSING',attempts=attempts+1,available_at=now()+interval '120 seconds' WHERE event_id=? AND state IN ('PENDING','PROCESSING') AND available_at<=now()",id)!=1) continue;
            var item=jdbc.queryForMap("SELECT * FROM stripe_webhook_inbox WHERE event_id=?",id);
            try {
                if(item.get("obligation_id")!=null) payments.reconcile((UUID)item.get("obligation_id"));
                if(item.get("customer_id")!=null) {
                    var users=jdbc.queryForList("SELECT user_id FROM stripe_customers WHERE customer_id=?",item.get("customer_id"));
                    for(var user:users) customers.refresh((UUID)user.get("user_id"));
                }
                jdbc.update("UPDATE stripe_webhook_inbox SET state='DONE' WHERE event_id=? AND attempts=?",id,item.get("attempts"));
            } catch(RuntimeException ex) {
                jdbc.update("UPDATE stripe_webhook_inbox SET state=?,available_at=now()+interval '60 seconds' WHERE event_id=? AND attempts=?",
                        ((Integer)item.get("attempts"))>=6?"REVIEW":"PENDING",id,item.get("attempts"));
            }
        }
    }
}
