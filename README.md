# AssinaFlow

[![CI](https://github.com/ricartefelipe/assinaflow/actions/workflows/ci.yml/badge.svg)](https://github.com/ricartefelipe/assinaflow/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Commercial-red.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3-FF6600?logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)](docker-compose.yml)

Sistema de gestão de assinaturas para streaming: renovação automática, cancelamento no fim do ciclo, proteção contra concorrência, idempotência e testes reprodutíveis com Testcontainers.

**Autor:** [Felipe Ricarte Magalhães](https://github.com/ricartefelipe) · [Site](https://codigodeproducao.com.br/) · [LinkedIn](https://www.linkedin.com/in/felipe-ricarte-magalhaes/)

Base package: `br.com.ricarte.assinaflow`

---

## Índice

- [Visão geral](#visão-geral)
- [Quando usar](#quando-usar)
- [Stack](#stack)
- [Quick Start](#quick-start)
- [Assunções (explícitas)](#assunções-explícitas)
- [Endpoints](#endpoints)
- [Observabilidade](#observabilidade)
- [Testes](#testes-maven)
- [Licença](#licença)

---

## Visão geral

| Área | Descrição |
|------|-----------|
| **Assinaturas** | No máximo 1 ativa por usuário; cancelamento sem cortar acesso antes do fim do ciclo |
| **Renovação** | Job em UTC com recuperação de atraso e retry determinístico de cobrança (até 3 tentativas) |
| **Concorrência** | Lock no PostgreSQL + idempotência no consumidor para execução multi-instância |
| **Mensageria** | RabbitMQ + outbox com retry, backoff e estado DEAD |
| **Cache** | Redis para assinatura ativa |
| **Observabilidade** | `X-Request-Id`, logs estruturados e métricas Prometheus |

---

## Quando usar

- Você precisa modelar **assinatura recorrente** com regras claras de ciclo, cancelamento e suspensão
- Quer **confiabilidade multi-instância** (sem cobrança duplicada) com evidência em testes
- Precisa de **outbox + idempotência** como parte do desenho, não como afterthought

---

## Stack

| Camada | Tecnologia |
|--------|------------|
| Runtime | Java 21, Spring Boot 3.x (Maven) |
| Dados | PostgreSQL 16+, Liquibase YAML, JPA/Hibernate |
| Cache / filas | Redis, RabbitMQ |
| API | OpenAPI / Swagger (Springdoc) |
| Qualidade | JUnit 5, Mockito, Testcontainers |
| Ops | Docker Compose, Actuator + Micrometer/Prometheus |

**Ferramentas:** Maven 3.9+ (projeto em `backend/`), Node.js 20+ para o portal em `frontend/`, Docker Compose V2.

---

## Quick Start

```bash
docker compose up --build
```

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- RabbitMQ UI: `http://localhost:15672` (`guest` / `guest`)

Testes rápidos (`cd backend`): `mvn test`  
Com Testcontainers: `mvn verify -P integration-tests`

---

## Assunções (explícitas)
1) Timezone: UTC para calculo de datas e vencimento
2) Semantica de dataExpiracao: dia de cobranca e limite do ciclo
    - Ciclo interpretado como intervalo [dataInicio, dataExpiracao)
    - Renovacao bem sucedida move:
        - dataInicio = dataExpiracao
        - dataExpiracao = dataExpiracao + 1 mes
    - Assinaturas com dataExpiracao <= hoje UTC entram na fila de renovacao (recupera atraso)
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

- GET `/api/v1/users/{userId}/subscriptions/active` (`204` quando nao houver assinatura ativa; JWT + ownership)
- GET `/api/v1/users/{userId}/subscriptions/{subscriptionId}` (JWT + ownership)
- GET `/api/v1/users/{userId}/subscriptions` (JWT + ownership)
- POST `/api/v1/users/{userId}/subscriptions/cancel` (JWT + ownership)
- POST `/api/v1/users/{userId}/subscriptions/resume` (JWT + ownership)
- POST `/api/v1/users/{userId}/subscriptions/reactivate` (JWT + ownership)
- POST `/api/v1/users/{userId}/subscriptions/change-plan` (JWT + ownership; proration no upgrade/crédito no downgrade)

---

## Portal do assinante e console admin

Com a API em `http://localhost:8080`:

```bash
cd frontend
npm install
npm run dev
```

O Vite faz proxy de `/api` para a API. Rotas principais:
- `/` portal e planos
- `/entrar` login (ADMIN vai para `/admin`)
- `/admin` dashboard operacional
- `/admin/clientes` usuários + payment profile
- `/admin/assinaturas` ciclos e renovação
- `/admin/operacoes` outbox (requeue)

### Demo pública (portfolio EC2)

- UI: `http://54.94.163.136:9084/`
- API / Swagger: `http://54.94.163.136:8080/swagger-ui/index.html`
- Credenciais demo: `demo@assinaflow.test` / `demo12345` (role ADMIN)
- Login nativo com e-mail/senha (hash local). Credenciais de test drive devem ser provisionadas no banco do AssinaFlow; o TotalRecall não é modo de login.

Stack completa com UI:

```bash
docker compose up -d --build
```

A UI sobe em `http://localhost:9084` com proxy same-origin para a API.

Para promover um admin: `UPDATE users SET role = 'ADMIN' WHERE email = 'seu@email';` e faca login de novo.

Abra `http://localhost:5173`. Fluxo: landing de planos → cadastro/login → contratar (cobra via gateway) → conta (status e cancelamento).

Smoke e2e (Playwright), com a API em `http://localhost:8080`:

```bash
cd frontend
npm run test:e2e
```

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

---

## Licença

Licença comercial — ver [LICENSE](LICENSE) e [README-COMERCIAL.md](README-COMERCIAL.md).

**Autor:** Felipe Ricarte Magalhães
