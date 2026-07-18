# Design: backlog completo (gateway, proration, notificações, admin, e2e)

Data: 2026-07-18  
Base: `develop`  
Entrega: cinco PRs sequenciais `feature/*` → `develop` (sem pausa entre itens)

## Objetivo

Completar o backlog restante do starter kit AssinaFlow com fatias verticais testáveis.

## Decisões fechadas

| Tema | Decisão |
|------|---------|
| Entrega | Cinco PRs sequenciais |
| Gateway | Interface + `SimulatedPaymentGateway` (default) + `HttpPaymentGateway` stub |
| Contratação | Cobra preço cheio; só ativa se aprovado |
| Proration | Upgrade cobra diferença proporcional; downgrade troca + crédito na próxima renovação |
| Notificações | Outbox por `eventType` + sender log (default) / SMTP opcional |
| Admin | Role `ADMIN` + API `/api/v1/admin/**` + UI `/admin` |
| E2E | Playwright smoke no frontend; API em `:8080`; job CI opcional |
| Tom | Branches/PRs/commits discretos; sem menções a ferramentas de assistência |

## Fora de escopo

Stripe/SDK real, Mailhog obrigatório, impersonate, edição de catálogo de planos, estorno em cartão no downgrade.

---

## PR 1 — Gateway de pagamento

### Comportamento

- Introduzir `PaymentGateway.charge(PaymentChargeCommand)` com `userId`, `amountCents`, `idempotencyKey`, `description`.
- `SimulatedPaymentGateway`: lógica atual de `PaymentService` (ALWAYS_APPROVE / ALWAYS_DECLINE / FAIL_NEXT_N).
- `HttpPaymentGateway`: POST JSON para `app.payments.http.url`; resposta `{ "approved": bool, "errorCode"?, "errorMessage"? }`.
- Seleção via `app.payments.gateway=simulated|http` (default `simulated`).
- `PaymentService` permanece como fachada (métricas + delegação ao gateway).
- `SubscriptionService.create`: cobra `plan.priceCents` antes de persistir; recusa → `400 PAYMENT_DECLINED` sem criar assinatura.
- Renovação continua usando `PaymentService.charge`.

### Testes

- Unit: gateway simulado; create aprova/recusa.
- Http gateway: WireMock ou mock `RestClient` se já houver padrão; senão teste unitário com servidor embutido mínimo / mock.

---

## PR 2 — Proration na troca de plano

### Fórmula

Dias restantes = `max(0, expirationDate - today)` (UTC).  
Dias do ciclo = `max(1, expirationDate - startDate)`.  
Valor diário plano = `priceCents / diasCiclo` (inteiro, floor).  
Diferença = `(newDaily - oldDaily) * diasRestantes`.

- Se diferença > 0 (upgrade): cobrar via gateway; falha → não altera plano.
- Se diferença ≤ 0 (downgrade/igual preço efetivo): altera plano; acumula `|diferença|` em `subscription.renewal_credit_cents`.
- Ciclo (`startDate`/`expirationDate`) permanece.
- Renovação: `amount = max(0, plan.priceCents - renewalCreditCents)`; zera crédito após cobrança bem-sucedida; se recusar, crédito permanece.

### Persistência

Liquibase: coluna `renewal_credit_cents INT NOT NULL DEFAULT 0` em `subscriptions`.

### Portal

Texto em `/trocar-plano` explicando cobrança proporcional / crédito.

---

## PR 3 — Notificações

### Eventos

Enfileirar no outbox (mesmo `outbox_events`, `eventType` distinto):

- `NOTIFICATION_SUBSCRIPTION_CREATED`
- `NOTIFICATION_RENEWAL_SUCCEEDED`
- `NOTIFICATION_RENEWAL_FAILED`
- `NOTIFICATION_SUBSCRIPTION_SUSPENDED`
- `NOTIFICATION_PLAN_CHANGED`

Payload JSON: `userId`, `email`, `subscriptionId`, campos contextuais.

### Entrega

- `NotificationPublisher` no ciclo do outbox (ou ramo no `OutboxPublisher` por `eventType`):
  - eventos `PAYMENT_CHARGE_REQUESTED` → Rabbit (como hoje);
  - eventos `NOTIFICATION_*` → `NotificationSender`.
- `LoggingNotificationSender` (default): log INFO com destinatário e assunto.
- `SmtpNotificationSender` se `app.notifications.sender=smtp` (+ host/port/from).
- Default `app.notifications.sender=logging`.
- Async pagamento: se Rabbit desabilitado, notificações ainda processam via publisher local do outbox (scheduler).

### Testes

- Unit: enqueue após create; sender logging chamado ao publicar evento de notificação.

---

## PR 4 — Admin

### Modelo

- Coluna `role VARCHAR NOT NULL DEFAULT 'USER'` (`USER` | `ADMIN`).
- JWT inclui `ROLE_ADMIN` ou `ROLE_USER`.
- Seed/documentação: criar admin via SQL ou endpoint interno não; documentar update manual / Liquibase changeset opcional com usuário seed desabilitado por default.
- Endpoint `POST /api/v1/auth/register` sempre cria `USER`.

### API (`hasRole('ADMIN')`)

- `GET /api/v1/admin/users` — lista paginada simples (limit/offset).
- `GET /api/v1/admin/subscriptions` — lista recente.
- `GET /api/v1/admin/outbox?status=DEAD` — listar dead letters.
- `POST /api/v1/admin/outbox/{id}/requeue` — volta para PENDING.
- `PUT /api/v1/admin/users/{userId}/payment-profile` — behavior + failNextN.

### UI

Rota `/admin` (protegida: só ADMIN; senão redirect). Tabelas simples + ações requeue / payment profile.

---

## PR 5 — Playwright e2e

- `@playwright/test` no `frontend/`.
- Specs smoke: cadastro → contratar; conta → trocar plano (se possível com crédito/upgrade); cancelar.
- `webServer`: Vite; API externa `http://localhost:8080` (documentar pré-requisito).
- Script `npm run test:e2e`.
- Workflow GitHub opcional `workflow_dispatch` (não bloqueia CI principal).

---

## Ordem de merge

1. `feature/payment-gateway`
2. `feature/plan-proration`
3. `feature/notifications`
4. `feature/admin-console`
5. `feature/playwright-e2e`
