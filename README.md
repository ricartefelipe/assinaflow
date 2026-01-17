# AssinaFlow
Sistema de gestao de assinaturas para streaming, com renovacao automatica no vencimento, cancelamento no fim do ciclo, protecao contra concorrencia e testes reprodutiveis.

Base package: `br.com.ricarte.assinaflow`

Requisitos atendidos conforme enunciado do desafio. :contentReference[oaicite:0]{index=0}

---

## Checklist rapido do avaliador (10 linhas)
1. Suba: `docker compose up --build`
2. Swagger: `http://localhost:8080/swagger-ui.html`
3. Crie usuario: `POST /api/v1/users`
4. Crie assinatura: `POST /api/v1/users/{userId}/subscriptions`
5. Impede 2 ativas: segunda criacao retorna 409
6. Cancelamento: `POST /api/v1/users/{userId}/subscriptions/cancel` e mantem ate expirar
7. Renovacao no vencimento UTC: crie assinatura vencendo hoje e verifique expiracao +1 mes
8. Falha 3x suspende: ajuste paymentProfile para ALWAYS_DECLINE e valide status SUSPENSA
9. Metricas: `GET /actuator/prometheus`
10. Testes: `cd backend && mvn test`

---

## Visao geral
O AssinaFlow implementa:
- Cadastro de usuarios
- Criacao de assinatura com no maximo 1 ativa por usuario
- Cancelamento sem cortar acesso antes do fim do ciclo
- Renovacao automatica no dia do vencimento em UTC
- Retry deterministico de cobranca ate 3 tentativas, com suspensao na 3a falha
- Confiabilidade em multi instancia com lock no Postgres e idempotencia no consumidor

Inclui diferenciais opcionais:
- RabbitMQ para cobranca assincrona
- Outbox Pattern com retry, backoff e DEAD no banco
- Redis para cache da assinatura ativa
- Observabilidade com requestId e metricas Prometheus

---

## Stack
- Java 21
- Spring Boot 3.x
- PostgreSQL 16+
- Liquibase YAML
- JPA Hibernate
- Testes com JUnit 5, Mockito e Testcontainers
- Docker Compose e Dockerfile
- OpenAPI Swagger via Springdoc
- Logs estruturados com correlacao via X Request Id
- Actuator Micrometer Prometheus

---

## Assuncoes (explicitas)
1) Timezone: UTC para calculo de datas e vencimento
2) Semantica de dataExpiracao: dia de cobranca e limite do ciclo
    - Ciclo interpretado como intervalo [dataInicio, dataExpiracao)
    - Renovacao bem sucedida move:
        - dataInicio = dataExpiracao
        - dataExpiracao = dataExpiracao + 1 mes
3) Cancelamento:
    - status vira CANCELAMENTO_AGENDADO
    - autoRenew vira false
    - dataExpiracao nao muda, nao corta acesso
    - nao renova apos expirar, job diario finaliza para CANCELADA
4) Retry de cobranca:
    - 1a falha, proxima tentativa em +15 min
    - 2a falha, proxima tentativa em +60 min
    - 3a falha, status SUSPENSA e autoRenew false
    - cada tentativa gera registro em subscription_renewal_attempts
5) Modo assincrono:
    - entrega at least once
    - consumidor idempotente por constraint unica no banco
    - outbox publisher reintenta e marca DEAD apos maxAttempts

---

## Endpoints
Base URL: `http://localhost:8080`

Swagger UI:
- `/swagger-ui.html`

OpenAPI JSON:
- `/v3/api-docs`

### Usuarios
- POST `/api/v1/users`
- GET `/api/v1/users/{userId}`
- PUT `/api/v1/users/{userId}/payment-profile`

### Assinaturas
- POST `/api/v1/users/{userId}/subscriptions`
- GET `/api/v1/users/{userId}/subscriptions/active`
- GET `/api/v1/users/{userId}/subscriptions`
- POST `/api/v1/users/{userId}/subscriptions/cancel`

---

## Persistencia
Principais tabelas:
- users
- payment_profiles
- subscriptions
- subscription_renewal_attempts
- outbox_events (extra, modo assincrono)

Regras criticas no banco:
- 1 assinatura ativa por usuario: indice unico parcial em subscriptions(user_id) para status ATIVA e CANCELAMENTO_AGENDADO
- Idempotencia do consumidor: unique em subscription_renewal_attempts (subscription_id, cycle_expiration_date, attempt_number)

---

## Concorrencia e anti duplicidade
Renovacao no banco usa row lock com SKIP LOCKED, seguro com 2 instancias:
- Se duas instancias disputarem, apenas uma bloqueia e processa
- A outra ignora as linhas bloqueadas e nao duplica renovacao

No modo assincrono:
- O scheduler enfileira via outbox
- O publisher publica do outbox com SKIP LOCKED
- O consumer aplica idempotencia no banco

---

## Observabilidade
### Request Id
- Cabecalho: `X-Request-Id`
- Se nao vier, o servidor gera
- O valor aparece nos logs no campo requestId

### Metricas
- Prometheus: `/actuator/prometheus`
- Catalogo: `/actuator/metrics`

Metricas customizadas:
- payment_charge_total
- payment_charge_duration
- renewal_attempt_total
- subscription_suspended_total
- outbox_enqueued_total
- outbox_publish_total
- outbox_pending
- outbox_dead

---

## Perfis
- default: modo assincrono desabilitado, cache simple
- docker: modo assincrono habilitado, Redis habilitado, RabbitMQ habilitado

---

## Como rodar com Docker Compose
Na raiz do repositorio:

```bash
docker compose up --build
