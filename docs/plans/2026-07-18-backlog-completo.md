# Backlog completo — Implementation Plan

> **For agentic workers:** Execute PRs in order. Prefer inline execution in one session. Checkboxes track progress.

**Goal:** Entregar gateway, proration, notificações, admin e Playwright em cinco PRs sequenciais para `develop`.

**Architecture:** Fatias verticais; `PaymentGateway` unifica cobrança; proration usa gateway + crédito; notificações reutilizam outbox; admin por role JWT; e2e no frontend.

**Tech Stack:** Java 21, Spring Boot 3, Liquibase, React/Vite, Playwright.

## Global Constraints

- Commits/PRs discretos (sem menção a IA).
- Default gateway `simulated`; default notifications `logging`.
- Respostas e UI em português.
- Cada PR: `mvn test` relevante + `npm run build` quando tocar frontend; push + compare URL se `gh` sem auth.

---

### Task 1: PR payment-gateway

**Files:**
- Create: `payment/PaymentGateway.java`, `PaymentChargeCommand.java`, `SimulatedPaymentGateway.java`, `HttpPaymentGateway.java`, `PaymentGatewayConfig.java`
- Modify: `PaymentService.java`, `SubscriptionService.java`, `application.yml`, `SubscriptionServiceTest.java`, README
- Docs: spec/plan already in `docs/`

- [ ] Extrair simulação para `SimulatedPaymentGateway`; `PaymentService` delega
- [ ] `HttpPaymentGateway` + config `app.payments.gateway` / `app.payments.http.url`
- [ ] `create` cobra antes de salvar; recusa → `PAYMENT_DECLINED`
- [ ] Testes unitários + commit + push + compare `develop...feature/payment-gateway`

### Task 2: PR plan-proration

- [ ] Migration `renewal_credit_cents`
- [ ] `ProrationService` + `changePlan` cobra/credita
- [ ] Renovação aplica crédito
- [ ] Portal copy + testes + push compare

### Task 3: PR notifications

- [ ] `NotificationSender` + logging/smtp
- [ ] Enfileirar eventos nos pontos de domínio
- [ ] `OutboxPublisher` despacha por `eventType`
- [ ] Testes + push compare

### Task 4: PR admin-console

- [ ] Role no user + JWT
- [ ] Admin API + SecurityConfig
- [ ] UI `/admin`
- [ ] Testes + push compare

### Task 5: PR playwright-e2e

- [ ] Playwright config + smoke specs
- [ ] Scripts npm + workflow_dispatch opcional
- [ ] README + push compare
