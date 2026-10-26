package br.com.ricarte.assinaflow.payment.stripe;

import br.com.ricarte.assinaflow.IntegrationTestBase;
import br.com.ricarte.assinaflow.auth.JwtService;
import br.com.ricarte.assinaflow.subscription.PaymentService;
import br.com.ricarte.assinaflow.testutil.MutableTimeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(StripeContractIntegrationTest.Http.class)
@TestPropertySource(properties={"app.billing.mode=sandbox","app.payments.gateway=stripe","app.security.enabled=true",
        "app.security.jwt.secret=contract-sandbox-dedicated-secret-at-least32",
        "STRIPE_SECRET_KEY=sk_test_contract","STRIPE_PUBLISHABLE_KEY=pk_test_contract","STRIPE_WEBHOOK_SECRET=whsec_contract"})
class StripeContractIntegrationTest extends IntegrationTestBase {
    @TestConfiguration static class Http {
        private MockRestServiceServer server;
        @Bean @Primary RestClient.Builder stripeTestBuilder() {var b=RestClient.builder();server=MockRestServiceServer.bindTo(b).build();return b;}
        @Bean MockRestServiceServer stripeServer(RestClient.Builder builder) {return server;}
    }
    @Autowired MockRestServiceServer server;@Autowired MockMvc mvc;@Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;@Autowired JwtService jwt;@Autowired PaymentService payments;
    @Autowired br.com.ricarte.assinaflow.payment.PaymentLedger ledger;
    @Autowired StripeWebhooks webhooks;@Autowired StripeCustomers customers;@Autowired MutableTimeProvider clock;
    UUID user,obligation;String token;final String customer="cus_contract",method="pm_contract";String intentId;
    @BeforeEach void setup() throws Exception {
        server.reset();jdbc.update("DELETE FROM stripe_webhook_inbox");clock.setNow(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        String email=UUID.randomUUID()+"@example.test";
        var result=mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Sandbox\",\"email\":\""+email+"\"}"))
                .andExpect(status().isCreated()).andReturn();
        user=UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
        token="Bearer "+jwt.createToken(user,email,"USER");intentId="pi_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO stripe_customers(user_id,customer_id,payment_method) VALUES (?,?,?)",user,customer+user,method);
    }
    String object(String state,boolean error) throws Exception {
        var m=new LinkedHashMap<String,Object>();m.put("id",intentId);m.put("livemode",false);m.put("customer",customer+user);
        m.put("amount",1990);m.put("currency","brl");m.put("status",state);m.put("client_secret","test_ephemeral_secret");
        m.put("metadata",Map.of("obligation_id",obligation.toString(),"user_id",user.toString()));
        if(error)m.put("last_payment_error",Map.of("code","card_declined"));return mapper.writeValueAsString(m);
    }
    void createIntent() {
        server.expect(requestTo("https://api.stripe.com/v1/payment_intents")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Stripe-Version","2025-02-24.acacia"))
                .andRespond(request->{
                    String body=((org.springframework.mock.http.client.MockClientHttpRequest)request).getBodyAsString();
                    var form=new HashMap<String,String>();for(String p:body.split("&")){String[]kv=p.split("=",2);form.put(java.net.URLDecoder.decode(kv[0],StandardCharsets.UTF_8),java.net.URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}
                    obligation=UUID.fromString(form.get("metadata[obligation_id]"));
                    assertThat(form.get("customer")).isEqualTo(customer+user);assertThat(form.get("amount")).isEqualTo("1990");
                    assertThat(form).doesNotContainKeys("confirm","card[number]","card[cvc]");
                    assertThat(request.getHeaders().getFirst("Idempotency-Key")).isEqualTo("obligation-"+obligation+"-intent");
                    try{return withSuccess(object("requires_payment_method",false),MediaType.APPLICATION_JSON).createResponse(request);}catch(Exception e){throw new java.io.IOException(e);}
                });
    }
    void confirmation(String state,boolean error,int number) {
        server.expect(requestTo("https://api.stripe.com/v1/payment_intents/"+intentId+"/confirm"))
                .andExpect(method(HttpMethod.POST)).andExpect(request->{assertThat(request.getHeaders().getFirst("Idempotency-Key")).endsWith("-confirm-"+number);})
                .andRespond(request->{try{return error ? withStatus(HttpStatus.PAYMENT_REQUIRED).contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"payment_intent\":"+object(state,true)+"}}").createResponse(request) : withSuccess(object(state,false),MediaType.APPLICATION_JSON).createResponse(request);}catch(Exception e){throw new java.io.IOException(e);}});
    }
    void query(String state,boolean error) {
        server.expect(requestTo("https://api.stripe.com/v1/payment_intents/"+intentId)).andExpect(method(HttpMethod.GET))
                .andRespond(request->{try{return withSuccess(object(state,error),MediaType.APPLICATION_JSON).createResponse(request);}catch(Exception e){throw new java.io.IOException(e);}});
    }
    org.springframework.test.web.servlet.ResultActions subscribe() throws Exception {
        return mvc.perform(post("/api/v1/users/{user}/subscriptions",user).header("Authorization",token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"plano\":\"BASICO\"}"));
    }
    @Test void approvedSubscriptionUsesRealAdapterAndDuplicateRequestCannotCreateAnotherPayment() throws Exception {
        createIntent();confirmation("succeeded",false,1);subscribe().andExpect(status().isCreated());
        for(int i=0;i<10;i++)subscribe().andExpect(status().isConflict());server.verify();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stripe_operations WHERE obligation_id=?",Integer.class,obligation)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_obligations WHERE id=?",String.class,obligation)).isEqualTo("APPROVED");
        var sub=jdbc.queryForMap("SELECT id,expiration_date FROM subscriptions WHERE user_id=?",user);
        var expiration=((java.sql.Date)sub.get("expiration_date")).toLocalDate();
        clock.setNow(expiration.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        server.reset();intentId="pi_"+UUID.randomUUID().toString().replace("-","");createIntent();confirmation("succeeded",false,1);
        payments.renew(user,(UUID)sub.get("id"),expiration,1990);server.verify();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stripe_operations s JOIN payment_obligations p ON p.id=s.obligation_id WHERE p.user_id=?",Integer.class,user)).isEqualTo(2);
    }
    @Test void definitiveDeclineRequiresExplicitRetryAndNewConfirmationKeyOnSameIntent() throws Exception {
        createIntent();confirmation("requires_payment_method",true,1);subscribe().andExpect(status().isBadRequest());server.verify();server.reset();
        confirmation("succeeded",false,2);subscribe().andExpect(status().isCreated());server.verify();
        assertThat(jdbc.queryForObject("SELECT confirmations FROM stripe_operations WHERE obligation_id=?",Integer.class,obligation)).isEqualTo(2);
    }
    @Test void lostConfirmationResponseRecoversByLookupWithoutAnotherPost() throws Exception {
        createIntent();server.expect(requestTo("https://api.stripe.com/v1/payment_intents/"+intentId+"/confirm"))
                .andRespond(request->{throw new java.io.IOException("Controlled lost response");});
        subscribe().andExpect(status().isServiceUnavailable());server.verify();server.reset();
        query("succeeded",false);clock.setNow(clock.now().plusSeconds(16));payments.reconcile(obligation);server.verify();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=?",Integer.class,user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT confirmations FROM stripe_operations WHERE obligation_id=?",Integer.class,obligation)).isEqualTo(1);
    }
    @Test void actionRequiredDoesNotGrantAccessAndOnlyServerLookupSettlesIt() throws Exception {
        createIntent();confirmation("requires_action",false,1);subscribe().andExpect(status().isServiceUnavailable());server.verify();server.reset();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=?",Integer.class,user)).isZero();
        query("requires_action",false);
        mvc.perform(post("/api/v1/users/{user}/payments/{id}/stripe/action",user,obligation).header("Authorization",token)).andExpect(status().isOk());server.verify();server.reset();
        query("succeeded",false);clock.setNow(clock.now().plusSeconds(16));payments.reconcile(obligation);server.verify();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=?",Integer.class,user)).isEqualTo(1);
    }
    byte[] event(String id,String type) throws Exception {return mapper.writeValueAsBytes(Map.of("id",id,"livemode",false,"type",type,"data",Map.of("object",Map.of("id",intentId,"metadata",Map.of("obligation_id",obligation.toString())))));}
    String signature(byte[] data,long timestamp) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("whsec_contract".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));return "t="+timestamp+",v1="+HexFormat.of().formatHex(mac.doFinal(data));
    }
    @Test void webhookSignatureDedupeAndOldFailureCannotUndoApproval() throws Exception {
        createIntent();confirmation("processing",false,1);subscribe().andExpect(status().isServiceUnavailable());server.verify();server.reset();
        byte[] event=event("evt_test","payment_intent.succeeded");long now=Instant.now().getEpochSecond();
        mvc.perform(post("/api/v1/billing/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(event).header("Stripe-Signature",signature(event,now-301))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/billing/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(event).header("Stripe-Signature","t="+now+",v1=00")).andExpect(status().isBadRequest());
        for(int i=0;i<2;i++)mvc.perform(post("/api/v1/billing/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(event).header("Stripe-Signature",signature(event,now))).andExpect(status().isOk());
        query("succeeded",false);clock.setNow(clock.now().plusSeconds(16));webhooks.process();server.verify();server.reset();
        byte[] old=event("evt_old","payment_intent.payment_failed");webhooks.receive(old,signature(old,now));webhooks.process();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stripe_webhook_inbox",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_obligations WHERE id=?",String.class,obligation)).isEqualTo("APPROVED");server.verify();
    }
    @Test void webhookAndRecoveryRaceCannotDuplicateSettlement() throws Exception {
        createIntent();confirmation("processing",false,1);subscribe().andExpect(status().isServiceUnavailable());server.verify();server.reset();
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        server.expect(requestTo("https://api.stripe.com/v1/payment_intents/"+intentId)).andRespond(request->{
            entered.countDown();
            try {if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new java.io.IOException("Barrier not released");return withSuccess(object("succeeded",false),MediaType.APPLICATION_JSON).createResponse(request);}
            catch(Exception ex){throw new java.io.IOException(ex);}
        });
        clock.setNow(clock.now().plusSeconds(16));
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var worker=pool.submit(()->payments.reconcile(obligation));
            assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            byte[] data=event("evt_race","payment_intent.succeeded");webhooks.receive(data,signature(data,Instant.now().getEpochSecond()));
            pool.submit(()->webhooks.process()).get(5,java.util.concurrent.TimeUnit.SECONDS);
            release.countDown();worker.get(5,java.util.concurrent.TimeUnit.SECONDS);
        } finally {release.countDown();}
        server.verify();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=?",Integer.class,user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT confirmations FROM stripe_operations WHERE obligation_id=?",Integer.class,obligation)).isEqualTo(1);
    }
    @Test void providerMismatchCannotGrantAccess() throws Exception {
        createIntent();server.expect(requestTo("https://api.stripe.com/v1/payment_intents/"+intentId+"/confirm"))
                .andRespond(request->{try{return withSuccess(object("succeeded",false).replace("1990","2990"),MediaType.APPLICATION_JSON).createResponse(request);}catch(Exception e){throw new java.io.IOException(e);}});
        subscribe().andExpect(status().isServiceUnavailable());server.verify();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=?",Integer.class,user)).isZero();
    }
    @Test void webhookOrBrowserReconcileCannotAuthorizeAnUnsentCharge() throws Exception {
        var date=clock.now().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        var o=ledger.prepare(user,1990,"subscribe|"+user+"|BASICO|"+date+"|0");
        payments.reconcile(o.id());
        assertThat(jdbc.queryForObject("SELECT status FROM payment_obligations WHERE id=?",String.class,o.id())).isEqualTo("PREPARED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stripe_operations WHERE obligation_id=?",Integer.class,o.id())).isZero();server.verify();
    }
    @Test void reconcileCannotAccelerateDeclinedRenewalButAuthorizedDunningUsesNewConfirmation() throws Exception {
        createIntent();confirmation("succeeded",false,1);subscribe().andExpect(status().isCreated());server.verify();server.reset();
        var sub=jdbc.queryForMap("SELECT id,expiration_date FROM subscriptions WHERE user_id=?",user);
        var expiration=((java.sql.Date)sub.get("expiration_date")).toLocalDate();clock.setNow(expiration.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        intentId="pi_"+UUID.randomUUID().toString().replace("-","");createIntent();confirmation("requires_payment_method",true,1);
        payments.renew(user,(UUID)sub.get("id"),expiration,1990);server.verify();server.reset();
        Object due=jdbc.queryForObject("SELECT next_attempt_at FROM payment_obligations WHERE id=?",Object.class,obligation);
        payments.reconcile(obligation);server.verify();
        assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM payment_obligations WHERE id=?",Object.class,obligation)).isEqualTo(due);
        assertThat(jdbc.queryForObject("SELECT confirmations FROM stripe_operations WHERE obligation_id=?",Integer.class,obligation)).isEqualTo(1);
        clock.setNow(clock.now().plusSeconds(16*60));confirmation("succeeded",false,2);
        payments.renew(user,(UUID)sub.get("id"),expiration,1990);server.verify();
        assertThat(jdbc.queryForObject("SELECT confirmations FROM stripe_operations WHERE obligation_id=?",Integer.class,obligation)).isEqualTo(2);
    }
    @Test void missingIntentAfterLostCreationIsNotPermissionToCreateAnother() throws Exception {
        server.expect(requestTo("https://api.stripe.com/v1/payment_intents")).andRespond(request->{throw new java.io.IOException("Controlled lost creation response");});
        subscribe().andExpect(status().isServiceUnavailable());server.verify();server.reset();
        obligation=jdbc.queryForObject("SELECT id FROM payment_obligations WHERE user_id=?",UUID.class,user);
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.stripe.com/v1/payment_intents/search?")))
                .andRespond(withSuccess("{\"data\":[],\"has_more\":false}",MediaType.APPLICATION_JSON));
        clock.setNow(clock.now().plusSeconds(16));payments.reconcile(obligation);server.verify();server.reset();
        assertThat(jdbc.queryForObject("SELECT status FROM payment_obligations WHERE id=?",String.class,obligation)).isEqualTo("UNKNOWN");
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.stripe.com/v1/payment_intents/search?")))
                .andRespond(withSuccess("{\"data\":[],\"has_more\":false}",MediaType.APPLICATION_JSON));
        clock.setNow(clock.now().plusSeconds(49*3600));payments.reconcile(obligation);server.verify();
        assertThat(jdbc.queryForObject("SELECT status FROM payment_obligations WHERE id=?",String.class,obligation)).isEqualTo("REVIEW");
        assertThat(jdbc.queryForObject("SELECT confirmations FROM stripe_operations WHERE obligation_id=?",Integer.class,obligation)).isZero();
    }
    @Test void customerCreationUsesStableKeyAndOnlySandboxObjects() throws Exception {
        jdbc.update("DELETE FROM stripe_customers WHERE user_id=?",user);
        server.expect(requestTo("https://api.stripe.com/v1/customers")).andExpect(header("Idempotency-Key","customer-"+user))
                .andRespond(withSuccess("{\"id\":\""+customer+user+"\",\"livemode\":false}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions"))
                .andRespond(withSuccess("{\"id\":\"cs_test_customer\",\"livemode\":false,\"customer\":\""+customer+user+"\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_customer\"}",MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/v1/users/{id}/billing/setup",user).header("Authorization",token)).andExpect(status().isOk());server.verify();
        assertThatThrownBy(()->new StripeApi(RestClient.builder(),mapper,"sk_live_forbidden")).isInstanceOf(IllegalStateException.class);
    }
    @Test void hostedSetupIsVerifiedRemotelyAndCrossUserAccessIsRejected() throws Exception {
        jdbc.update("UPDATE stripe_customers SET payment_method=NULL WHERE user_id=?",user);
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"cs_test_fixture\",\"livemode\":false,\"customer\":\""+customer+user+"\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_fixture\"}",MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/v1/users/{id}/billing/setup",user).header("Authorization",token)).andExpect(status().isOk());server.verify();server.reset();
        String base="https://api.stripe.com/v1";
        server.expect(requestTo(base+"/checkout/sessions/cs_test_fixture")).andRespond(withSuccess("{\"id\":\"cs_test_fixture\",\"livemode\":false,\"customer\":\""+customer+user+"\",\"status\":\"complete\",\"setup_intent\":\"seti_test\"}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(base+"/setup_intents/seti_test")).andRespond(withSuccess("{\"id\":\"seti_test\",\"livemode\":false,\"customer\":\""+customer+user+"\",\"status\":\"succeeded\",\"payment_method\":\"pm_contract\"}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(base+"/payment_methods/pm_contract")).andRespond(withSuccess("{\"id\":\"pm_contract\",\"livemode\":false,\"customer\":\""+customer+user+"\"}",MediaType.APPLICATION_JSON));
        assertThat(customers.refresh(user)).isTrue();server.verify();
        mvc.perform(post("/api/v1/users/{id}/billing/setup",UUID.randomUUID()).header("Authorization",token)).andExpect(status().isForbidden());
    }
}
