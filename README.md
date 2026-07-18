# AssinaFlow
Sistema de gestao de assinaturas para streaming, com renovacao automatica no vencimento, cancelamento no fim do ciclo, protecao contra concorrencia e testes reprodutiveis.

Base package: `br.com.ricarte.assinaflow`

---

## Ferramentas (build e testes)
- **Java:** 21 (Temurin ou equivalente recomendado)
- **Maven:** 3.9+, instalacao no sistema; nao há `mvnw` neste repositorio (`mvn test` deve resolver o projeto em `backend/`)
- **Node.js:** 20+ para o portal em `frontend/` (`npm install` / `npm run dev`)
- **Docker / Docker Compose:** para subir a stack (`docker compose up`) e para os testes de integracao (`Testcontainers`); o comando `compose` deve ser o mesmo que o Compose V2 distribuido pelo Docker CLI

---

## Visao geral
O AssinaFlow implementa:
- Cadastro de usuarios
- Criacao de assinatura com no maximo 1 ativa por usuario
- Cancelamento sem cortar acesso antes do fim do ciclo
- Renovacao automatica no vencimento em UTC (inclui assinaturas atrasadas)
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
- Testes com JUnit 5, Mockito e Testcontainers (integracao marcada com tag `integration`; padrao do Maven ignora esse grupo até `-P integration-tests`)
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
    - Assinaturas com dataExpiracao <= hoje UTC entram na fila de renovacao (recupera atraso)3) Cancelamento:
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
    - evento DEAD da mesma tentativa e reenfileirado se a renovacao voltar a disputar a chave

---

## Endpoints
Base URL: `http://localhost:8080`

Swagger UI:
- `/swagger-ui.html`

OpenAPI JSON:
- `/v3/api-docs`

### Auth
- POST `/api/v1/auth/register`
- POST `/api/v1/auth/login`
- GET `/api/v1/auth/me` (JWT)

### Usuarios
- POST `/api/v1/users`
- GET `/api/v1/users/{userId}` (JWT + ownership)
- PUT `/api/v1/users/{userId}/payment-profile` (JWT + ownership)

### Planos
- GET `/api/v1/plans`

### Admin
- GET `/api/v1/admin/users` (ROLE_ADMIN)
- GET `/api/v1/admin/subscriptions` (ROLE_ADMIN)
- GET `/api/v1/admin/outbox?status=DEAD` (ROLE_ADMIN)
- POST `/api/v1/admin/outbox/{id}/requeue` (ROLE_ADMIN)
- PUT `/api/v1/admin/users/{userId}/payment-profile` (ROLE_ADMIN)

Para promover um admin: `UPDATE users SET role = 'ADMIN' WHERE email = 'seu@email';` e faca login de novo.

- GET `/api/v1/users/{userId}/subscriptions/active` (`204` quando nao houver assinatura ativa; JWT + ownership)
- GET `/api/v1/users/{userId}/subscriptions/{subscriptionId}` (JWT + ownership)
- GET `/api/v1/users/{userId}/subscriptions` (JWT + ownership)
- POST `/api/v1/users/{userId}/subscriptions/cancel` (JWT + ownership)
- POST `/api/v1/users/{userId}/subscriptions/resume` (JWT + ownership)
- POST `/api/v1/users/{userId}/subscriptions/reactivate` (JWT + ownership)
- POST `/api/v1/users/{userId}/subscriptions/change-plan` (JWT + ownership; proration no upgrade/crédito no downgrade)

---

## Portal do assinante
Em um terminal, com a API em `http://localhost:8080`:

```bash
cd frontend
npm install
npm run dev
```

Abra `http://localhost:5173`. Fluxo: landing de planos → cadastro/login → contratar (cobra via gateway) → conta (status e cancelamento).

Variaveis uteis da API:
- `JWT_SECRET` (minimo 32 caracteres)
- `APP_CORS_ORIGINS` (padrao inclui `http://localhost:5173`)
- `APP_PAYMENTS_GATEWAY` (`simulated` padrao, ou `http`)
- `APP_PAYMENTS_HTTP_URL` (URL do stub quando gateway=`http`)
- `APP_NOTIFICATIONS_SENDER` (`logging` padrao, ou `smtp`)
- `APP_NOTIFICATIONS_FROM` (remetente quando sender=`smtp`)
- `spring.mail.host` / `spring.mail.port` (SMTP)

A contratacao cobra o preco do plano via `PaymentGateway` antes de ativar. Com `simulated`, o comportamento segue o payment-profile do usuario (`ALWAYS_APPROVE` / `ALWAYS_DECLINE` / `FAIL_NEXT_N`).
- `APP_SECURITY_ENABLED` (`false` desliga autenticacao; util em testes)

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
- **default**: modo assincrono desabilitado, cache simples
- **docker** (`SPRING_PROFILES_ACTIVE=docker` no Compose): modo assincrono habilitado, Redis habilitado, RabbitMQ habilitado

---

## Testes (Maven)

| Objetivo | Comando (`cd backend`) |
|----------|-------------------------|
| Apenas rapido (JUnit sem tag `integration`) | `mvn test` |
| Compilacao + testes incluindo Testcontainers | `mvn verify -P integration-tests` |

Por padrao, `integration` fica **excluido** pelo Surefire: desenvolvedores sem Docker continuam rodando `mvn test` em ciclo rapido.

Se `-P integration-tests` falhar com erro de cliente Docker na JVM, atualize Docker Engine/Desktop ou configure o cliente exigido pelo `docker-java` usado pelo Testcontainers; em runners Linux típicos (por exemplo Ubuntu no GitHub Actions) o comando acima deve passar quando o daemon Docker esta disponível.

---

## Como rodar com Docker Compose
Na raiz do repositorio:

```bash
docker compose up --build
```

O servico `app` monta backend com `SPRING_PROFILES_ACTIVE=docker`, Postgres, Redis e RabbitMQ (`5672`; interface de gerencia em `http://localhost:15672`, credenciais `guest`/`guest`).

Portas liberadas tambem incluem Postgres `5432` e Redis `6379` quando precisar de clientes externos.
