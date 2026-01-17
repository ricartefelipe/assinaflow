# AssinaFlow - Sistema de Gestao de Assinaturas (Streaming)

## Visao geral
O **AssinaFlow** implementa um sistema de gestao de assinaturas para um servico de streaming, com:
- cadastro de usuarios
- criacao de assinatura (no maximo 1 assinatura ativa por usuario)
- cancelamento sem cortar acesso antes do fim do ciclo
- renovacao automatica no dia do vencimento (UTC)
- retry deterministico de cobranca ate 3 tentativas; suspensao na 3a falha

Os requisitos base estao no enunciado do desafio.

## Diferenciais incluidos (opcionais do desafio)
- **RabbitMQ** para processamento assincrono de cobrancas
- **Outbox Pattern** (PostgreSQL -> publisher -> RabbitMQ) com:
  - `FOR UPDATE SKIP LOCKED` no publisher (seguro com multiplas instancias)
  - retry/backoff deterministico
  - transicao para **DEAD** apos `maxAttempts` (configuravel)
- **Redis** para cache da assinatura ativa por usuario (TTL 60s)
- **Observabilidade**
  - logs estruturados com `requestId` (cabecalho `X-Request-Id`)
  - Actuator + Micrometer com scrape **Prometheus**

## Stack
- Java 21
- Spring Boot 3.x
- PostgreSQL 16+
- Liquibase (YAML)
- JPA/Hibernate
- Testes: JUnit 5 + Mockito + Testcontainers (Postgres + Rabbit quando aplicavel)
- Docker Compose + Dockerfile

## Pacote base
- `br.com.ricarte.assinaflow`

---

## Assuncoes de negocio (explicitas)
1) **Timezone**: UTC para todos os calculos de data.
2) **Semantica de `dataExpiracao`**: representa o **dia de cobranca** (billing date) e o limite do ciclo.
   - O ciclo e interpretado como **[dataInicio, dataExpiracao)**.
   - Em renovacao bem-sucedida:
     - `dataInicio = dataExpiracao`
     - `dataExpiracao = dataExpiracao + 1 mes`
3) **Cancelamento**:
   - status vira `CANCELAMENTO_AGENDADO`
   - `autoRenew=false`
   - o usuario mantem acesso ate `dataExpiracao`
   - nao renova apos expirar
4) **Retry de pagamento** (padrao seguro e deterministico):
   - 1a falha -> proxima tentativa em +15 min
   - 2a falha -> proxima tentativa em +60 min
   - 3a falha -> `SUSPENSA` e `autoRenew=false`
   - toda tentativa e persistida em `subscription_renewal_attempts`
5) **Modo assincrono (RabbitMQ)**:
   - publicacao e **at-least-once**
   - consumidor e **idempotente** via constraint unica: `(subscription_id, cycle_expiration_date, attempt_number)`
   - o publisher do outbox faz retry/backoff e marca como `DEAD` apos `maxAttempts`
6) **Redis cache**:
   - TTL 60s
   - mutacoes (create/cancel/renew/suspend) fazem `evict` das chaves

---

## Observabilidade
### Logs estruturados + correlacao
- O filtro `RequestIdFilter` propaga/gera `X-Request-Id`.
- O `requestId` entra no MDC e aparece nos logs.

### Metricas
- Prometheus: `GET /actuator/prometheus`
- Catalogo: `GET /actuator/metrics`

Metricas customizadas (baixa cardinalidade):
- `payment_charge_total{approved=...}`
- `payment_charge_duration`
- `renewal_attempt_total{success=...,mode=sync|async}`
- `subscription_suspended_total{mode=...}`
- `outbox_enqueued_total{eventType=...}`
- `outbox_publish_total{success=...}`
- `outbox_pending` (gauge)
- `outbox_dead` (gauge)

---

## Perfis
- **default**: async desabilitado, cache simples (in-memory)
- **docker**: async habilitado, Redis habilitado, RabbitMQ habilitado

---

## Subir com Docker Compose
Na raiz do repositorio:

```bash
docker compose up --build
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- RabbitMQ Management: http://localhost:15672 (usuario/senha: guest/guest)

Parar e remover volumes:

```bash
docker compose down -v
```

---

## Demo em 5 minutos (modo assincrono: Outbox + RabbitMQ + Redis)
> O `docker-compose.yml` ja sobe a aplicacao com `SPRING_PROFILES_ACTIVE=docker` e `APP_PAYMENTS_ASYNC_ENABLED=true`.

### 1) Criar usuario

```bash
USER_ID=$(curl -s -X POST http://localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","nome":"Demo","paymentProfile":{"behavior":"ALWAYS_APPROVE","failNextN":0}}' | jq -r .id)

echo "USER_ID=$USER_ID"
```

### 2) Criar assinatura vencendo hoje (UTC)
O objetivo aqui e criar uma assinatura cujo `dataExpiracao = hoje(UTC)`.

- Linux (GNU date):

```bash
START_DATE=$(date -u -d '1 month ago' +%F)
```

- macOS (BSD date):

```bash
START_DATE=$(date -u -v -1m +%F)
```

Criar assinatura:

```bash
curl -i -X POST http://localhost:8080/api/v1/users/$USER_ID/subscriptions \
  -H 'Content-Type: application/json' \
  -d "{\"plano\":\"PREMIUM\",\"dataInicio\":\"$START_DATE\"}"
```

### 3) Validar renovacao
O scheduler roda a cada 5 minutos (UTC). Em ate alguns minutos, valide:

```bash
curl -s http://localhost:8080/api/v1/users/$USER_ID/subscriptions/active | jq
```

Voce deve ver `dataExpiracao` avancar +1 mes apos a renovacao.

---

## Playbook de validacao (manual)

### A) Fluxo assincrono ponta a ponta (Outbox -> RabbitMQ -> Consumer -> Renew)
1) Crie uma assinatura vencendo hoje (ver demo acima).
2) Acompanhe logs do container `app`:

```bash
docker compose logs -f app
```

### B) Outbox retry/backoff e DEAD-letter quando o broker esta fora
Objetivo: provar que o outbox **sobrevive a indisponibilidade do RabbitMQ** e nao perde eventos.

1) Pare o RabbitMQ:

```bash
docker compose stop rabbitmq
```

2) Crie uma assinatura vencendo hoje (ou reutilize uma ja vencendo hoje).

3) Inspecione o outbox no Postgres (deve permanecer PENDING e reagendar com backoff):

```bash
docker compose exec postgres psql -U subscriptions -d subscriptions -c "
select id, event_type, status, publish_attempts, next_attempt_at, dead_at, left(last_error,120) as last_error
from outbox_events
order by created_at desc
limit 10;"
```

4) Suba o RabbitMQ novamente:

```bash
docker compose start rabbitmq
```

5) Aguarde alguns segundos e revalide o outbox:
- status deve virar `SENT`
- a assinatura deve ser renovada

#### Demo acelerada de DEAD (opcional)
Por padrao `maxAttempts=10`. Para chegar em DEAD rapidamente, ajuste no `docker-compose.yml`:

```yml
APP_OUTBOX_PUBLISHER_MAX_ATTEMPTS: "3"
```

Rebuild/restart:

```bash
docker compose up --build
```

Mantenha o RabbitMQ parado e observe a transicao para `DEAD`.

### C) Validar cache Redis (assinatura ativa)
1) Chame o endpoint de assinatura ativa repetidas vezes:

```bash
curl -s http://localhost:8080/api/v1/users/$USER_ID/subscriptions/active | jq
curl -s http://localhost:8080/api/v1/users/$USER_ID/subscriptions/active | jq
```

2) Dispare uma mutacao (cancelar) e valide que o cache foi invalidado:

```bash
curl -s -X POST http://localhost:8080/api/v1/users/$USER_ID/subscriptions/cancel | jq
curl -s http://localhost:8080/api/v1/users/$USER_ID/subscriptions/active | jq
```

### D) Metricas
Prometheus scrape:

```bash
curl -s http://localhost:8080/actuator/prometheus | head
```

Gauges do outbox:

```bash
curl -s http://localhost:8080/actuator/metrics/outbox_pending | jq
curl -s http://localhost:8080/actuator/metrics/outbox_dead | jq
```

---

## Rodar local (sem Docker)
Pre-requisitos: Java 21 + Maven + Postgres 16+.

```bash
cd backend
mvn clean test
mvn spring-boot:run
```

Variaveis (se nao usar defaults):

```bash
export DB_URL=jdbc:postgresql://localhost:5432/subscriptions
export DB_USERNAME=subscriptions
export DB_PASSWORD=subscriptions
```

---

## Testes
> Testcontainers requer Docker instalado na sua maquina.

Rodar tudo:

```bash
cd backend
mvn test
```

Rodar somente o fluxo assincrono (RabbitMQ + Outbox + Consumer):

```bash
cd backend
mvn -Dtest=AsyncPaymentsIntegrationTest test
```

Rodar somente o teste de resiliencia do Outbox (broker fora -> retry -> DEAD):

```bash
cd backend
mvn -Dtest=OutboxRetryDeadLetterIntegrationTest test
```

---

## Concorrencia (evitar renovacao duplicada)
- O job de renovacao usa row-lock no Postgres com `FOR UPDATE SKIP LOCKED`.
- Cada assinatura e processada em transacao isolada (`REQUIRES_NEW`).
- No modo async, o job tambem usa `renewal_in_flight_until` para evitar enfileiramento repetido enquanto a mensagem esta em voo.

---

## Trade-offs e alternativas
- `@Scheduled` vs Quartz: `@Scheduled` reduz pegada e atende ao desafio. Quartz e superior para misfire handling, clustering e schedules complexos.
- Pagamento sincrono vs assincrono: sincrono e mais simples; assincrono melhora resiliencia quando o provedor e lento/instavel.
- Outbox at-least-once: evita perda de mensagem; exige consumidor idempotente (feito via constraint no banco).
