# AssinaFlow

[![CI](https://github.com/ricartefelipe/assinaflow/actions/workflows/ci.yml/badge.svg)](https://github.com/ricartefelipe/assinaflow/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Commercial-red.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3-FF6600?logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)](docker-compose.yml)

Sistema de gestão de assinaturas recorrentes com foco em confiabilidade operacional.

**Estado da entrega:** demonstração e adapter Stripe sandbox integrado; homologação externa pendente, cobrança real bloqueada.
Leia a [matriz de fechamento](docs/validation/closure.md), o [manual operacional](docs/validation/operations.md) e a [oferta delimitada](README-COMERCIAL.md).

O projeto implementa ciclos, cancelamento, retry e outbox. Locks e constraints protegem estado local; não comprovam ausência de cobrança duplicada após sucesso remoto seguido de falha local. Há ledger e reconciliação duráveis, simulador explícito e adapter Stripe sandbox conectado à jornada. Contratos HTTP locais foram testados; homologação externa depende das credenciais de teste.

## Visão geral

| Área | Regra |
|------|-------|
| **Assinaturas** | No máximo uma assinatura ativa por usuário |
| **Cancelamento** | Acesso permanece válido até o fim do ciclo |
| **Renovação** | Job em UTC com recuperação de atrasos |
| **Retry** | Tentativas determinísticas antes da suspensão |
| **Concorrência** | Row lock + `SKIP LOCKED` |
| **Idempotência** | Obrigação por ciclo, chave estável, lease e reconciliação durável; Stripe sandbox integrado; homologação externa pendente |
| **Mensageria** | RabbitMQ + outbox |
| **Cache** | Redis |
| **Observabilidade** | Request ID, logs e métricas Actuator |

## Stack

| Camada | Tecnologia |
|--------|------------|
| Backend | Java 21, Spring Boot 4.0.9 / Spring 7.0.10 |
| Dados | PostgreSQL 16+, JPA/Hibernate, Liquibase |
| Cache | Redis |
| Mensageria | RabbitMQ |
| API | REST, OpenAPI, Swagger |
| Testes | JUnit 5, Mockito, Testcontainers |
| Operação | Docker Compose, Actuator, Micrometer |
| Frontend | Vite / Node.js 24 validado |

Base package:

```text
br.com.ricarte.assinaflow
```

## Regras de negócio

### Ciclo

O sistema usa UTC para cálculo de datas.

Cada ciclo é interpretado como:

```text
[dataInicio, dataExpiracao)
```

Uma renovação bem-sucedida faz:

```text
dataInicio = dataExpiracao
dataExpiracao = dataExpiracao + 1 mês
```

Assinaturas vencidas entram novamente na fila de renovação, permitindo recuperação de atrasos.

### Cancelamento

Ao cancelar:

- status passa para `CANCELAMENTO_AGENDADO`;
- `autoRenew` passa para `false`;
- `dataExpiracao` não é alterada;
- o acesso permanece válido até o fim do ciclo;
- após expirar, o job finaliza a assinatura como `CANCELADA`.

### Retry de cobrança

| Tentativa | Próxima ação |
|----------|--------------|
| 1ª falha | nova tentativa em 15 min |
| 2ª falha | nova tentativa em 60 min |
| 3ª falha | assinatura passa para `SUSPENSA` e `autoRenew=false` |

Cada tentativa é registrada em `subscription_renewal_attempts`.

### Processamento assíncrono

- entrega `at least once`;
- consumidor idempotente;
- outbox publisher com retry e backoff;
- eventos que excedem o limite de tentativas passam para `DEAD`;
- eventos `DEAD` podem ser reenfileirados.

## Concorrência e idempotência

A renovação usa lock no PostgreSQL com `SKIP LOCKED`.

Com duas ou mais instâncias:

1. uma instância bloqueia a assinatura;
2. as demais ignoram a linha bloqueada;
3. a linha não é processada simultaneamente pelo outro scheduler; isso não substitui idempotência externa e reconciliação.

No fluxo assíncrono:

```text
Scheduler
   |
   v
Outbox
   |
   v
RabbitMQ
   |
   v
Consumer idempotente
   |
   v
PostgreSQL
```

O registro local de tentativa é protegido por constraint única em:

```text
(subscription_id, cycle_expiration_date, attempt_number)
```

## Quick Start

Na raiz do repositório:

```bash
docker compose up --build
```

Serviços principais:

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- RabbitMQ UI: `http://localhost:15672`

Testes rápidos:

```bash
cd backend
mvn test
```

Integração com Testcontainers:

```bash
mvn verify -P integration-tests
```

## API

### Autenticação

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`

### Usuários

- `GET /api/v1/users/{userId}`
- `PUT /api/v1/users/{userId}/payment-profile`

### Planos

- `GET /api/v1/plans`

### Assinaturas

- `GET /api/v1/users/{userId}/subscriptions/active`
- `GET /api/v1/users/{userId}/subscriptions/{subscriptionId}`
- `GET /api/v1/users/{userId}/subscriptions`
- `POST /api/v1/users/{userId}/subscriptions/cancel`
- `POST /api/v1/users/{userId}/subscriptions/resume`
- `POST /api/v1/users/{userId}/subscriptions/reactivate`
- `POST /api/v1/users/{userId}/subscriptions/change-plan`

O endpoint de alteração de plano calcula proporção no upgrade e crédito no downgrade. Política, limites e fluxos ainda não suportados estão no manual operacional; não constitui faturamento financeiro homologado.

### Cobranças do titular

- `GET /api/v1/users/{userId}/payments`
- `GET /api/v1/users/{userId}/payments/{paymentId}`

### Administração

- `GET /api/v1/admin/payments/review` — fila de resultados inconclusivos, somente ADMIN.

- `GET /api/v1/admin/users`
- `GET /api/v1/admin/subscriptions`
- `GET /api/v1/admin/outbox?status=DEAD`
- `POST /api/v1/admin/outbox/{id}/requeue`
- `PUT /api/v1/admin/users/{userId}/payment-profile`

## Portal do assinante e console admin

Com a API disponível em `http://localhost:8080`:

```bash
cd frontend
npm ci --ignore-scripts
npm run dev
```

Rotas principais:

- `/` — landing e planos
- `/entrar` — autenticação
- `/admin` — dashboard
- `/admin/clientes` — clientes e payment profile
- `/admin/assinaturas` — ciclos e renovações
- `/admin/operacoes` — outbox e requeue

A UI usa proxy de `/api` para o backend.

## Demonstração isolada

Não há credencial pública necessária ao teste. Após `mvn verify -P integration-tests`, execute na raiz:

```bash
python3 scripts/verify-isolated.py
# Com Chromium da versão Playwright instalada:
python3 scripts/verify-isolated.py --frontend
```

O script cria PostgreSQL descartável, executa cadastro/login e autorização, recupera obrigação incerta após reinício da JVM com consulta ao simulador, restaura backup em banco vazio e lê assinatura e pagamento pela API reiniciada. Não usa ambiente existente. O browser roda sem retries e o pagamento é simulado.

## Fluxo funcional

```text
landing
→ cadastro / login
→ contratação
→ cobrança pelo gateway
→ assinatura ativa
→ renovação automática
→ retry quando necessário
→ cancelamento ou suspensão
```

O gateway `simulated` permite controlar o resultado do pagamento pelo payment profile:

- `ALWAYS_APPROVE`
- `ALWAYS_DECLINE`
- `FAIL_NEXT_N`

## Configuração

Variáveis principais:

- `APP_BILLING_MODE`
- `JWT_SECRET`
- `APP_CORS_ORIGINS`
- `APP_PAYMENTS_GATEWAY`
- `APP_PAYMENTS_HTTP_URL`
- `APP_NOTIFICATIONS_SENDER`
- `APP_NOTIFICATIONS_FROM`
- `spring.mail.host`
- `spring.mail.port`
- `APP_SECURITY_ENABLED`

`APP_BILLING_MODE=demo` exige `APP_PAYMENTS_GATEWAY=simulated` (padrão).
`APP_BILLING_MODE=sandbox` com `APP_PAYMENTS_GATEWAY=stripe` exige autenticação, JWT dedicado, STRIPE_SECRET_KEY de teste, STRIPE_PUBLISHABLE_KEY e STRIPE_WEBHOOK_SECRET. Cadastro do cartão ocorre no Checkout hospedado, e autenticação adicional no componente Stripe.js.
`production` é rejeitado enquanto não houver homologação. O HTTP genérico não tem contrato de consulta e é rejeitado na inicialização do serviço financeiro. O simulador e o adapter Stripe implementam submit/lookup e recuperação durável; webhooks Stripe são autenticados e deduplicados persistentemente. Timeouts e resultados inconclusivos são persistidos para consulta/revisão, sem nova chave nem recusa presumida. Não apontar o transporte antigo a cobranças reais.

`APP_NOTIFICATIONS_SENDER` aceita `logging` ou `smtp`.

## Persistência

Principais tabelas:

- `users`
- `payment_profiles`
- `subscriptions`
- `subscription_renewal_attempts`
- `outbox_events`
- `payment_obligations`
- `payment_executions`
- `simulated_provider_payments`

Regras importantes no banco:

- índice único parcial garante uma assinatura ativa por usuário;
- constraint de idempotência evita repetição de tentativa no mesmo ciclo.

## Observabilidade

### Request ID

A API utiliza `X-Request-Id`.

Se o header não for enviado, o servidor gera um valor automaticamente e o inclui nos logs.

### Métricas

Catálogo: `/actuator/metrics`. A dependência do registry Prometheus não está presente; não anunciar `/actuator/prometheus` funcional nesta distribuição.

Métricas de domínio:

- `payment_charge_total`
- `payment_charge_duration`
- `renewal_attempt_total`
- `subscription_suspended_total`
- `outbox_enqueued_total`
- `outbox_publish_total`
- `outbox_pending`
- `outbox_dead`

## Profiles

### `default`

- processamento assíncrono desabilitado;
- cache simples.

### `docker`

- processamento assíncrono habilitado;
- Redis habilitado;
- RabbitMQ habilitado.

## Testes

| Objetivo | Comando |
|----------|---------|
| Unitários | `cd backend && mvn test` |
| Integração | `cd backend && mvn verify -P integration-tests` |
| E2E frontend | `cd frontend && npm run test:e2e` |

Os testes de integração utilizam Testcontainers e exigem um daemon Docker disponível.

## Operação

JWT exige issuer/audience e assinatura; tokens antigos exigem novo login. Usuários desabilitados/expirados e papéis removidos têm efeito na próxima requisição. Não há refresh token nem logout com revogação individual. As rotas `/users/{id}` são exclusivas do titular; ADMIN atua em `/admin`, sem bypass de ownership. Auditoria administrativa completa permanece pendente.

Backup/restore, atualização, incidentes e homologação: [manual operacional](docs/validation/operations.md). Docker Compose é demonstração com defaults locais, não configuração de produção.


Para promover um usuário a administrador em ambiente local:

```sql
UPDATE users
SET role = 'ADMIN'
WHERE email = 'seu@email';
```

Depois, faça login novamente para obter um token com o novo papel.

## Licença

Licença comercial — ver [LICENSE](LICENSE) e [README-COMERCIAL.md](README-COMERCIAL.md).