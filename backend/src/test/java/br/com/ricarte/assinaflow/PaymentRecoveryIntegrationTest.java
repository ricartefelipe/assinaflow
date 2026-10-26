package br.com.ricarte.assinaflow;

import br.com.ricarte.assinaflow.common.exception.ConflictException;
import br.com.ricarte.assinaflow.metrics.BillingMetrics;
import br.com.ricarte.assinaflow.notification.*;
import br.com.ricarte.assinaflow.outbox.OutboxPublisher;
import br.com.ricarte.assinaflow.payment.*;
import br.com.ricarte.assinaflow.subscription.*;
import br.com.ricarte.assinaflow.testutil.MutableTimeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(PaymentRecoveryIntegrationTest.Config.class)
class PaymentRecoveryIntegrationTest extends IntegrationTestBase {
    @Autowired PaymentLedger ledger;
    @Autowired PaymentService payments;
    @Autowired PaymentSettlement settlement;
    @Autowired BillingMetrics metrics;
    @Autowired ControlledGateway provider;
    @Autowired ControlledSender sender;
    @Autowired MutableTimeProvider clock;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired SubscriptionService service;
    @Autowired RenewalService renewals;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired OutboxPublisher publisher;

    @BeforeEach void reset() { provider.reset(); sender.fail.set(false); clock.setNow(Instant.parse("2025-04-30T00:00:00Z")); }

    private SubscriptionEntity fixture() throws Exception {
        String email="finance-"+UUID.randomUUID()+"@example.test";
        var res=mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Synthetic\",\"email\":\""+email+"\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID user=UUID.fromString(mapper.readTree(res.getResponse().getContentAsString()).path("id").asText());
        var s=new SubscriptionEntity(); s.setUserId(user); s.setPlan(Plan.BASICO);
        s.setStatus(SubscriptionStatus.ATIVA); s.setAutoRenew(true);
        s.setStartDate(LocalDate.parse("2025-03-30")); s.setExpirationDate(LocalDate.parse("2025-04-30"));
        return subscriptions.saveAndFlush(s);
    }
    private String key(SubscriptionEntity s) { return "renewal|"+s.getId()+"|"+s.getExpirationDate(); }
    private PaymentObligation prepare(SubscriptionEntity s) { return ledger.prepare(s.getUserId(),1990,key(s)); }
    private void renew(SubscriptionEntity s) { payments.renew(s.getUserId(),s.getId(),s.getExpirationDate(),1990); }
    private void advance(long seconds) { clock.setNow(clock.now().plusSeconds(seconds)); }
    private int effects(UUID user) { return jdbc.queryForObject("SELECT coalesce(sum(effects),0)::int FROM simulated_provider_payments WHERE user_id=?",Integer.class,user); }
    private PaymentService restartedWorker() { return new PaymentService(provider,metrics,ledger,settlement); }
    private SubscriptionEntity read(SubscriptionEntity s) { return subscriptions.findById(s.getId()).orElseThrow(); }

    @Test void equalPricesInTwoCyclesCreateTwoObligationsAndTenReplaysHaveOneEffect() throws Exception {
        var s=fixture();
        for(int i=0;i<10;i++) renew(s);
        assertThat(effects(s.getUserId())).isEqualTo(1);
        var next=read(s); renew(next);
        assertThat(effects(s.getUserId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_obligations WHERE user_id=?",Integer.class,s.getUserId())).isEqualTo(2);
        assertThat(read(s).getExpirationDate()).isEqualTo(LocalDate.parse("2025-06-30"));
    }

    @Test void twoIndependentWorkersAndConnectionsCannotSubmitSameObligationTwice() throws Exception {
        var s=fixture(); prepare(s);
        try(var a=dataSource.getConnection();var b=dataSource.getConnection()) {
            var ra=a.createStatement().executeQuery("select pg_backend_pid()");ra.next();
            var rb=b.createStatement().executeQuery("select pg_backend_pid()");rb.next();
            assertThat(ra.getInt(1)).isNotEqualTo(rb.getInt(1));
        }
        provider.entered=new CountDownLatch(1); provider.release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<?> first=pool.submit(()->renew(s));
            assertThat(provider.entered.await(10,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(()->restartedWorker().renew(s.getUserId(),s.getId(),s.getExpirationDate(),1990));
            second.get(10,TimeUnit.SECONDS);
            provider.release.countDown(); first.get(10,TimeUnit.SECONDS);
        } finally { provider.release.countDown(); }
        assertThat(effects(s.getUserId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_executions e JOIN payment_obligations o ON o.id=e.obligation_id WHERE o.user_id=? AND operation='CHARGE'",Integer.class,s.getUserId())).isEqualTo(1);
    }

    @Test void crashAfterIntentBeforeSendIsQueriedThenReviewedNeverBlindlyRetried() throws Exception {
        var s=fixture(); var o=prepare(s); provider.crashBefore=true;
        assertThatThrownBy(()->renew(s)).isInstanceOf(SimulatedCrash.class);
        assertThat(ledger.get(o.id()).status()).isEqualTo("IN_FLIGHT");
        provider.crashBefore=false; advance(121);
        for(int i=0;i<6;i++) { restartedWorker().recover(100); advance(901); }
        assertThat(ledger.get(o.id()).status()).isEqualTo("REVIEW");
        assertThat(effects(s.getUserId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_executions WHERE obligation_id=? AND operation='CHARGE'",Integer.class,o.id())).isEqualTo(1);
    }

    @Test void timeoutAfterAcceptanceReconcilesWithoutAnotherCharge() throws Exception {
        var s=fixture(); provider.loseResponse=true;
        renew(s);
        var o=prepare(s);
        assertThat(o.status()).isEqualTo("UNKNOWN"); assertThat(effects(s.getUserId())).isEqualTo(1);
        assertThat(read(s).getRenewalFailures()).isZero();
        provider.loseResponse=false; advance(16); restartedWorker().recover(100);
        assertThat(ledger.get(o.id()).closed()).isTrue();
        assertThat(read(s).getExpirationDate()).isEqualTo(LocalDate.parse("2025-05-30"));
        assertThat(effects(s.getUserId())).isEqualTo(1);
    }

    @Test void crashAfterRemoteSuccessBeforeLocalRecordRecoversAfterWorkerRestart() throws Exception {
        var s=fixture(); var o=prepare(s); provider.crashAfter=true;
        assertThatThrownBy(()->renew(s)).isInstanceOf(SimulatedCrash.class);
        assertThat(ledger.get(o.id()).status()).isEqualTo("IN_FLIGHT");
        assertThat(effects(s.getUserId())).isEqualTo(1);
        provider.crashAfter=false; advance(121); restartedWorker().recover(100);
        assertThat(ledger.get(o.id()).closed()).isTrue(); assertThat(effects(s.getUserId())).isEqualTo(1);
    }

    @Test void recordedSuccessSurvivesCrashBeforeDomainCommit() throws Exception {
        var s=fixture(); var o=prepare(s); var claim=ledger.claim(o.id());
        var receipt=provider.submit(claim.command()); ledger.record(claim,receipt);
        assertThat(read(s).getExpirationDate()).isEqualTo(s.getExpirationDate());
        advance(121); restartedWorker().recover(100);
        assertThat(read(s).getExpirationDate()).isEqualTo(s.getExpirationDate().plusMonths(1));
        assertThat(effects(s.getUserId())).isEqualTo(1);
    }

    @Test void delayedNotFoundIsNotProofOfNoDebitAndExpiredWindowRequiresReview() throws Exception {
        var s=fixture(); var o=prepare(s); provider.loseResponse=true; renew(s);
        provider.loseResponse=false; provider.hideLookup=true; advance(16);
        restartedWorker().recover(100);
        assertThat(ledger.get(o.id()).status()).isEqualTo("UNKNOWN");
        advance(49*3600); restartedWorker().recover(100);
        assertThat(ledger.get(o.id()).status()).isEqualTo("REVIEW"); assertThat(effects(s.getUserId())).isEqualTo(1);
    }

    @Test void delayedVisibilityEventuallySettlesSameReference() throws Exception {
        var s=fixture(); var o=prepare(s); provider.loseResponse=true; renew(s);
        provider.loseResponse=false; provider.hideLookup=true; advance(16); payments.recover(100);
        provider.hideLookup=false; advance(31); payments.recover(100);
        assertThat(ledger.get(o.id()).status()).isEqualTo("APPROVED");
        assertThat(ledger.get(o.id()).closed()).isTrue(); assertThat(effects(s.getUserId())).isEqualTo(1);
    }

    @Test void immutableAmountCurrencyAndCorrelationAreEnforced() throws Exception {
        var s=fixture(); var o=prepare(s);
        assertThatThrownBy(()->ledger.prepare(s.getUserId(),2000,key(s))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE payment_obligations SET amount_cents=2000 WHERE id=?",o.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE payment_obligations SET currency='USD' WHERE id=?",o.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        provider.wrongAmount=true; renew(s);
        assertThat(ledger.get(o.id()).status()).isEqualTo("REVIEW");
        assertThat(read(s).getExpirationDate()).isEqualTo(s.getExpirationDate());
    }

    @Test void cancelBeforeAuthorizationPreventsDebit() throws Exception {
        var s=fixture(); var o=prepare(s); service.cancel(s.getUserId()); payments.recover(100);
        assertThat(ledger.get(o.id()).status()).isEqualTo("CANCELED"); assertThat(effects(s.getUserId())).isZero();
    }

    @Test void cancellationDuringAuthorizedDebitDoesNotBlockAndKeepsPaidPeriodWithoutAutoRenew() throws Exception {
        var s=fixture(); provider.entered=new CountDownLatch(1);provider.release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var charge=pool.submit(()->renew(s)); assertThat(provider.entered.await(10,TimeUnit.SECONDS)).isTrue();
            pool.submit(()->service.cancel(s.getUserId())).get(10,TimeUnit.SECONDS);
            provider.release.countDown(); charge.get(10,TimeUnit.SECONDS);
        } finally { provider.release.countDown(); }
        var updated=read(s);assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.CANCELAMENTO_AGENDADO);
        assertThat(updated.isAutoRenew()).isFalse();assertThat(updated.getExpirationDate()).isEqualTo(s.getExpirationDate().plusMonths(1));
        assertThat(effects(s.getUserId())).isEqualTo(1);
    }

    @Test void unknownDebitBlocksFinalCancellationUntilReconciled() throws Exception {
        var s=fixture();provider.loseResponse=true;renew(s);service.cancel(s.getUserId());
        renewals.finalizeScheduledCancellations(100);
        assertThat(read(s).getStatus()).isEqualTo(SubscriptionStatus.CANCELAMENTO_AGENDADO);
        provider.loseResponse=false;advance(16);payments.recover(100);
        assertThat(read(s).getExpirationDate()).isEqualTo(s.getExpirationDate().plusMonths(1));
    }

    @Test void outboxFailureAndReplayNeverRepeatFinancialEffect() throws Exception {
        var s=fixture();renew(s);sender.fail.set(true);publisher.publishPending(100);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND status='PENDING'",Integer.class,s.getId())).isEqualTo(1);
        sender.fail.set(false);jdbc.update("UPDATE outbox_events SET next_attempt_at=now()-interval '1 second' WHERE aggregate_id=?",s.getId());
        publisher.publishPending(100);renew(s);
        assertThat(effects(s.getUserId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND status='SENT'",Integer.class,s.getId())).isEqualTo(1);
    }

    @Test void persistedIntentBeforeAnyWorkerStartsSurvivesRestart() throws Exception {
        var s=fixture();var o=prepare(s);restartedWorker().recover(100);
        assertThat(ledger.get(o.id()).closed()).isTrue();assertThat(effects(s.getUserId())).isEqualTo(1);
    }

    @Test void monthEndAndLeapYearUseDocumentedClamping() throws Exception {
        for (String date: new String[]{"2024-02-29","2025-02-28"}) {
            var s=fixture(); s.setStartDate(LocalDate.parse(date).minusMonths(1));s.setExpirationDate(LocalDate.parse(date));subscriptions.saveAndFlush(s);
            renew(s);assertThat(read(s).getExpirationDate()).isEqualTo(LocalDate.parse(date).plusMonths(1));
        }
    }


    @Test void migrationPreservesExistingUsersSubscriptionsAttemptsAndOutbox() throws Exception {
        String database="upgrade_"+UUID.randomUUID().toString().replace("-","");
        assertThat(postgres.execInContainer("createdb","-U",postgres.getUsername(),database).getExitCode()).isZero();
        String url=postgres.getJdbcUrl().replace("/"+postgres.getDatabaseName(),"/"+database);
        try(var connection=java.sql.DriverManager.getConnection(url,postgres.getUsername(),postgres.getPassword())) {
            var db=liquibase.database.DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new liquibase.database.jvm.JdbcConnection(connection));
            var resources=new liquibase.resource.ClassLoaderResourceAccessor();
            new liquibase.Liquibase("db.changelog-pre-ledger.yaml",resources,db).update(new liquibase.Contexts(),new liquibase.LabelExpression());
            UUID user=UUID.randomUUID(), sub=UUID.randomUUID();
            var old=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            old.update("INSERT INTO users(id,email,nome) VALUES (?,?,'Previous data')",user,"old-"+user+"@example.test");
            old.update("INSERT INTO subscriptions(id,user_id,plan,start_date,expiration_date,status,renewal_credit_cents) VALUES (?,?,'BASICO','2025-03-30','2025-04-30','ATIVA',2500)",sub,user);
            old.update("INSERT INTO subscription_renewal_attempts(id,subscription_id,cycle_expiration_date,attempt_number,attempted_at,result,amount_cents) VALUES (?,?,'2025-03-30',1,now(),'SUCCESS',1990)",UUID.randomUUID(),sub);
            old.update("INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,idempotency_key,payload,status) VALUES (?,'SUBSCRIPTION',?,'PAYMENT_CHARGE_REQUESTED',?,'{}','DEAD')",UUID.randomUUID(),sub,"old|"+sub);
            new liquibase.Liquibase("db/changelog/db.changelog-master.yaml",resources,db).update(new liquibase.Contexts(),new liquibase.LabelExpression());
            assertThat(old.queryForObject("SELECT renewal_credit_cents FROM subscriptions WHERE id=?",Integer.class,sub)).isEqualTo(2500);
            assertThat(old.queryForObject("SELECT count(*) FROM subscription_renewal_attempts WHERE subscription_id=?",Integer.class,sub)).isEqualTo(1);
            assertThat(old.queryForObject("SELECT status FROM outbox_events WHERE aggregate_id=?",String.class,sub)).isEqualTo("DEAD");
            assertThat(old.queryForObject("SELECT count(*) FROM payment_obligations",Integer.class)).isZero();
        }
    }

    @Test void uncertainInitialSubscriptionAndPlanChangeAreRecoveredWithDomainEffects() throws Exception {
        var existing=fixture();
        // Use a separate user without any subscription for initial purchase recovery.
        var empty=fixture();subscriptions.deleteById(empty.getId());
        var request=new br.com.ricarte.assinaflow.subscription.dto.CreateSubscriptionRequest();request.setPlano(Plan.BASICO);
        provider.loseResponse=true;
        assertThatThrownBy(()->service.create(empty.getUserId(),request)).isInstanceOf(PaymentOutcomeUnknownException.class);
        provider.loseResponse=false;advance(16);payments.recover(100);
        assertThat(subscriptions.findByUserIdOrderByCreatedAtDesc(empty.getUserId())).hasSize(1);
        assertThat(effects(empty.getUserId())).isEqualTo(1);
        clock.setNow(Instant.parse("2025-04-15T00:00:00Z"));
        var change=new br.com.ricarte.assinaflow.subscription.dto.ChangePlanRequest();change.setPlano(Plan.PREMIUM);
        provider.loseResponse=true;
        assertThatThrownBy(()->service.changePlan(existing.getUserId(),change)).isInstanceOf(PaymentOutcomeUnknownException.class);
        provider.loseResponse=false;advance(16);payments.recover(100);
        assertThat(read(existing).getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(effects(existing.getUserId())).isEqualTo(1);
    }

    @Test void reactivationIsRecoveredAndExcessCreditIsNotLost() throws Exception {
        var s=fixture();s.setStatus(SubscriptionStatus.SUSPENSA);s.setAutoRenew(false);s.setRenewalCreditCents(2500);subscriptions.saveAndFlush(s);
        provider.loseResponse=true;
        assertThatThrownBy(()->service.reactivate(s.getUserId())).isInstanceOf(PaymentOutcomeUnknownException.class);
        provider.loseResponse=false;advance(16);payments.recover(100);
        assertThat(read(s).getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        assertThat(read(s).getRenewalCreditCents()).isEqualTo(510);
        assertThat(read(s).getExpirationDate()).isEqualTo(LocalDate.parse("2025-05-30"));
    }


    @Test void definitiveDeclineCanRetrySameObligationAndKeyWithoutDuplicatingAnApproval() throws Exception {
        var s=fixture();
        jdbc.update("UPDATE payment_profiles SET behavior='FAIL_NEXT_N',fail_next_n=1 WHERE user_id=?",s.getUserId());
        renew(s);var o=prepare(s);assertThat(o.status()).isEqualTo("DECLINED");
        assertThat(read(s).getRenewalFailures()).isEqualTo(1);advance(901);payments.recover(100);
        var paid=ledger.get(o.id());assertThat(paid.status()).isEqualTo("APPROVED");assertThat(paid.key()).isEqualTo(o.key());
        assertThat(effects(s.getUserId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_obligations WHERE user_id=?",Integer.class,s.getUserId())).isEqualTo(1);
    }

    @Test void staleWorkerResponseCannotUndoLaterSettlement() throws Exception {
        var s=fixture();var o=prepare(s);var first=ledger.claim(o.id());var receipt=provider.submit(first.command());
        advance(121);restartedWorker().recover(100);
        assertThat(ledger.get(o.id()).closed()).isTrue();
        var stale=new ProviderPayment(ProviderPayment.Status.DECLINED,receipt.reference(),receipt.obligationId(),receipt.userId(),receipt.amountCents(),receipt.currency());
        assertThat(ledger.record(first,stale)).isNull();
        assertThat(ledger.get(o.id()).status()).isEqualTo("APPROVED");assertThat(effects(s.getUserId())).isEqualTo(1);
    }

    @Test void twoIndependentConsumersShareOneDurableFinancialIdentity() throws Exception {
        var s=fixture();prepare(s);
        var message=new PaymentChargeRequested(UUID.randomUUID(),s.getId(),s.getUserId(),s.getExpirationDate(),1,1990,clock.now());
        provider.entered=new CountDownLatch(1);provider.release=new CountDownLatch(1);
        var a=new PaymentChargeConsumer(restartedWorker(),subscriptions,ledger);var b=new PaymentChargeConsumer(restartedWorker(),subscriptions,ledger);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->a.onMessage(message));assertThat(provider.entered.await(10,TimeUnit.SECONDS)).isTrue();
            pool.submit(()->b.onMessage(message)).get(10,TimeUnit.SECONDS);
            provider.release.countDown();first.get(10,TimeUnit.SECONDS);
        } finally { provider.release.countDown(); }
        assertThat(effects(s.getUserId())).isEqualTo(1);
    }


    @Test void incompleteHttpAdapterCannotEnableFinancialProcessing() {
        var http = new HttpPaymentGateway(org.springframework.web.client.RestClient.builder(), "http://unused.invalid/charge");
        assertThatThrownBy(() -> new PaymentService(http,metrics,ledger,settlement)).isInstanceOf(IllegalStateException.class);
    }


    @Test void schedulerCommitsCycleIntentBeforeCallingProvider() throws Exception {
        var s=fixture();s.setStartDate(LocalDate.parse("1899-12-01"));s.setExpirationDate(LocalDate.parse("1900-01-01"));
        subscriptions.saveAndFlush(s);
        assertThat(renewals.processDueRenewals(1)).isEqualTo(1);
        assertThat(effects(s.getUserId())).isEqualTo(1);
        assertThat(read(s).getExpirationDate()).isEqualTo(LocalDate.parse("1900-02-01"));
        assertThat(prepare(s).closed()).isTrue();
    }


    @Test void initialDeclineRequiresExplicitRetryAndKeepsSameObligationAndKey() throws Exception {
        var empty=fixture();subscriptions.deleteById(empty.getId());
        jdbc.update("UPDATE payment_profiles SET behavior='ALWAYS_DECLINE' WHERE user_id=?",empty.getUserId());
        var request=new br.com.ricarte.assinaflow.subscription.dto.CreateSubscriptionRequest();request.setPlano(Plan.BASICO);
        assertThatThrownBy(()->service.create(empty.getUserId(),request)).isInstanceOf(br.com.ricarte.assinaflow.common.exception.BadRequestException.class);
        UUID id=jdbc.queryForObject("SELECT id FROM payment_obligations WHERE user_id=?",UUID.class,empty.getUserId());
        var declined=ledger.get(id);assertThat(declined.closed()).isTrue();
        jdbc.update("UPDATE payment_profiles SET behavior='ALWAYS_APPROVE' WHERE user_id=?",empty.getUserId());
        advance(901);restartedWorker().recover(100);assertThat(effects(empty.getUserId())).isZero();
        service.create(empty.getUserId(),request);
        assertThat(ledger.get(id).status()).isEqualTo("APPROVED");assertThat(ledger.get(id).key()).isEqualTo(declined.key());
        assertThat(effects(empty.getUserId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_obligations WHERE user_id=?",Integer.class,empty.getUserId())).isEqualTo(1);
    }


    @Test void reactivationOnAnotherDayAfterDefinitiveDeclineUsesNewContractedStart() throws Exception {
        var s=fixture();s.setStatus(SubscriptionStatus.SUSPENSA);s.setAutoRenew(false);subscriptions.saveAndFlush(s);
        jdbc.update("UPDATE payment_profiles SET behavior='ALWAYS_DECLINE' WHERE user_id=?",s.getUserId());
        assertThatThrownBy(()->service.reactivate(s.getUserId())).isInstanceOf(br.com.ricarte.assinaflow.common.exception.BadRequestException.class);
        advance(86400);jdbc.update("UPDATE payment_profiles SET behavior='ALWAYS_APPROVE' WHERE user_id=?",s.getUserId());
        service.reactivate(s.getUserId());
        assertThat(read(s).getStartDate()).isEqualTo(LocalDate.parse("2025-05-01"));
        assertThat(read(s).getExpirationDate()).isEqualTo(LocalDate.parse("2025-06-01"));
        assertThat(effects(s.getUserId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_obligations WHERE user_id=?",Integer.class,s.getUserId())).isEqualTo(2);
    }


    @Test void brokerPayloadCannotCreateAnUnreservedObligationOrChangeItsAmount() throws Exception {
        var s=fixture();var consumer=new PaymentChargeConsumer(restartedWorker(),subscriptions,ledger);
        consumer.onMessage(new PaymentChargeRequested(UUID.randomUUID(),s.getId(),s.getUserId(),s.getExpirationDate(),1,1990,clock.now()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_obligations WHERE user_id=?",Integer.class,s.getUserId())).isZero();
        var o=prepare(s);
        consumer.onMessage(new PaymentChargeRequested(UUID.randomUUID(),s.getId(),s.getUserId(),s.getExpirationDate(),1,2990,clock.now()));
        assertThat(effects(s.getUserId())).isZero();assertThat(ledger.get(o.id()).status()).isEqualTo("PREPARED");
    }


    @Test void explicitResumeReauthorizesCanceledUnsentRenewalWithoutChangingIdentity() throws Exception {
        var s=fixture();var o=prepare(s);service.cancel(s.getUserId());payments.recover(100);
        assertThat(ledger.get(o.id()).status()).isEqualTo("CANCELED");assertThat(effects(s.getUserId())).isZero();
        service.resume(s.getUserId());payments.recover(100);
        assertThat(ledger.get(o.id()).status()).isEqualTo("APPROVED");assertThat(ledger.get(o.id()).key()).isEqualTo(o.key());
        assertThat(effects(s.getUserId())).isEqualTo(1);assertThat(read(s).getExpirationDate()).isEqualTo(s.getExpirationDate().plusMonths(1));
    }

    @TestConfiguration static class Config {
        @Bean @Primary ControlledGateway controlledGateway(SimulatedPaymentGateway actual) { return new ControlledGateway(actual); }
        @Bean @Primary ControlledSender controlledSender() { return new ControlledSender(); }
    }
    static class SimulatedCrash extends Error { }
    static class ControlledSender implements NotificationSender {
        final AtomicBoolean fail=new AtomicBoolean();
        public void send(NotificationMessage msg) { if(fail.get()) throw new IllegalStateException("Synthetic publication failure"); }
    }
    static class ControlledGateway implements PaymentGateway {
        final SimulatedPaymentGateway actual;
        volatile boolean crashBefore,crashAfter,loseResponse,hideLookup,wrongAmount;
        volatile CountDownLatch entered,release;
        ControlledGateway(SimulatedPaymentGateway actual) { this.actual=actual; }
        void reset() { crashBefore=false;crashAfter=false;loseResponse=false;hideLookup=false;wrongAmount=false;entered=null;release=null; }
        public boolean supportsRecovery() { return true; }
        public PaymentResult charge(PaymentChargeCommand c) { return actual.charge(c); }
        public ProviderPayment submit(PaymentChargeCommand c) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).as("provider boundary must not retain domain transaction").isFalse();
            if(crashBefore) throw new SimulatedCrash();
            if(entered!=null) { entered.countDown();try { if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Latch timeout"); } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IllegalStateException(ex); } }
            var result=actual.submit(c);
            if(crashAfter) throw new SimulatedCrash();
            if(loseResponse) throw new PaymentOutcomeUnknownException();
            return wrongAmount?new ProviderPayment(result.status(),result.reference(),result.obligationId(),result.userId(),result.amountCents()+1,result.currency()):result;
        }
        public ProviderPayment lookup(PaymentChargeCommand c,String ref) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return hideLookup?new ProviderPayment(ProviderPayment.Status.NOT_FOUND,null,c.obligationId(),c.userId(),c.amountCents(),c.currency()):actual.lookup(c,ref);
        }
    }
}
