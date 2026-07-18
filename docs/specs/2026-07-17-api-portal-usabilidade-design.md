# Design: contrato da API e portal do assinante

Data: 2026-07-17  
Base: `develop`  
Entrega: GitFlow com dois PRs sequenciais (`feature/*` → `develop`)

## Objetivo

Melhorar a usabilidade do AssinaFlow em duas frentes:

1. Deixar o contrato da API previsível para integradores e para o portal.
2. Entregar um portal mínimo do assinante (React + Vite) com autenticação JWT, escolha de plano e gestão básica da assinatura.

Fora de escopo nesta entrega: gateway de pagamento real, reativação após `SUSPENSA`, mudança de plano, faturas, e-mail transacional, painel administrativo.

## Decisões já fechadas

| Tema | Decisão |
|------|---------|
| Organização | Dois PRs: API primeiro, portal depois |
| Empty state da assinatura ativa | `204 No Content` quando o usuário existe e não há assinatura ativa/agendada |
| Público do portal | Assinante final |
| Autenticação | E-mail + senha com JWT |
| Stack do portal | React + TypeScript + Vite |
| Onboarding | Cadastro + login no portal |
| Contratação | Catálogo + ativação simulada no portal |
| Layout | Landing de planos (catálogo primeiro) + área logada em painel único |
| Tom do trabalho | Branches/PRs/commits discretos, sem menções a ferramentas de assistência; código sem comentários desnecessários |

## PR 1 — `feature/api-contract-usability`

### Problema

O contrato atual atrapalha UI e integração:

- `GET .../subscriptions/active` responde `404` tanto para “sem assinatura” quanto para recurso inexistente em outros contextos.
- `Location` após criar assinatura aponta para `.../subscriptions/{id}`, mas esse GET não existe.
- Histórico de assinaturas de usuário inexistente devolve `[]` em vez de `404`.
- UUID inválido no path vira `500`.
- Não há catálogo público de planos/preços.
- OpenAPI está mínimo (sem tags humanas, exemplos nem códigos de erro).

### Comportamento desejado

#### Assinatura ativa

- Usuário inexistente → `404` `USER_NOT_FOUND`.
- Usuário existe, sem assinatura em `ATIVA` ou `CANCELAMENTO_AGENDADO` → `204` (sem corpo).
- Usuário existe com assinatura elegível → `200` + `SubscriptionResponse`.

#### Recurso por id

- Implementar `GET /api/v1/users/{userId}/subscriptions/{subscriptionId}`.
- Assinatura de outro usuário ou inexistente → `404` `SUBSCRIPTION_NOT_FOUND`.
- Manter o `Location` atual no `201` de criação (passa a ser resolvível).

#### Histórico

- Antes de listar, validar existência do usuário.
- Usuário inexistente → `404` `USER_NOT_FOUND`.
- Usuário sem assinaturas → `200` com `[]`.

#### Catálogo de planos

- `GET /api/v1/plans` (público).
- Resposta: lista com `plano`, `precoCentavos`, `moeda` (`BRL`).
- Fonte: enum `Plan` existente (sem tabela nova nesta fase).

#### Erros de path

- `MethodArgumentTypeMismatchException` (ex.: UUID inválido) → `400` `VALIDATION_ERROR` com `ProblemDetail`.

#### OpenAPI

- Tags: `Usuarios`, `Assinaturas`, `Planos`.
- Documentar respostas relevantes (`200`, `201`, `204`, `400`, `404`, `409`).
- Descrever semanticamente `CANCELAMENTO_AGENDADO` (acesso permanece até `dataExpiracao`).

### Não muda neste PR

- Modelo de pagamento simulado (`payment-profile`).
- Autenticação.
- Regras de renovação/outbox.
- Frontend.

### Testes (PR 1)

- Integração/unitário cobrindo: `204` sem ativa; `404` de usuário; GET por id; histórico com usuário inexistente; UUID inválido → `400`; catálogo de planos.
- Manter suíte existente verde (`mvn test` e, no CI, `verify -P integration-tests`).

## PR 2 — `feature/subscriber-portal`

### Problema

Não existe superfície para o assinante criar conta, escolher plano, ver status e cancelar.

### Backend (auth e ownership)

#### Modelo

- Liquibase: coluna `password_hash` em `users` (varchar, **nullable**).
- `/auth/register` grava o hash; `POST /api/v1/users` legado continua sem senha (lab/API).
- Login no portal só funciona se `password_hash` estiver presente.

#### Endpoints

| Método | Rota | Auth | Função |
|--------|------|------|--------|
| POST | `/api/v1/auth/register` | público | Cria usuário com senha (BCrypt), perfil `ALWAYS_APPROVE` por padrão |
| POST | `/api/v1/auth/login` | público | Retorna JWT + dados básicos |
| GET | `/api/v1/auth/me` | JWT | Usuário autenticado |
| GET | `/api/v1/plans` | público | Já do PR 1 |
| POST | `/api/v1/users/{userId}/subscriptions` | JWT | Só se `userId` == sujeito do token |
| GET | `/api/v1/users/{userId}/subscriptions/active` | JWT | Idem |
| GET | `/api/v1/users/{userId}/subscriptions` | JWT | Idem |
| POST | `/api/v1/users/{userId}/subscriptions/cancel` | JWT | Idem |
| GET | `/api/v1/users/{userId}/subscriptions/{id}` | JWT | Idem |

#### Segurança

- Spring Security + JWT (HMAC, secret via env `JWT_SECRET`).
- Rotas públicas: auth register/login, plans, swagger (dev), actuator health.
- Demais rotas de negócio de usuário/assinatura exigem JWT e ownership.
- `PUT .../payment-profile` permanece, mas protegido por JWT + ownership (não exposto no portal).
- CORS liberado para origem do frontend de desenvolvimento.

#### Tokens

- Access token JWT (ex.: 8h).
- Sem refresh token nesta versão.
- Claims: `sub` = userId, `email`.

### Frontend

#### Estrutura

- App em `frontend/` (Vite + React + TypeScript).
- Proxy de dev para `http://localhost:8080`.
- UI em português com acentuação correta.
- Visual sóbrio, tipografia distinta do stack default genérico; sem cards desnecessários no hero; landing full-bleed de conteúdo de planos como âncora.

#### Rotas

| Rota | Estado | Conteúdo |
|------|--------|----------|
| `/` | público | Landing com catálogo de planos + CTAs Entrar / Assinar |
| `/cadastro` | público | Nome, e-mail, senha |
| `/entrar` | público | E-mail, senha |
| `/conta` | autenticado | Painel único: status da assinatura (ou empty state), ações e atalho para planos |
| `/contratar` | autenticado | Escolha/confirmação de plano |

#### Fluxos

1. Visitante vê planos → Assinar → cadastro (ou login) → escolher plano → assinatura `ATIVA`.
2. Logado sem assinatura: empty state amigável (baseado em `204`) + CTA para contratar.
3. Logado com `ATIVA`: mostra plano, datas, `autoRenew`.
4. Logado com `CANCELAMENTO_AGENDADO`: copy clara de que o acesso segue até `dataExpiracao`.
5. Cancelar: confirmação simples → `POST .../cancel`.
6. `SUSPENSA`: mensagem objetiva de que a renovação falhou; sem reativação nesta versão.

#### Empty states e erros

- `204` na ativa → empty state, não toast de erro.
- `ProblemDetail` → mensagem amigável; `requestId` visível só em detalhe técnico colapsado (opcional).
- Formulários: validação client-side mínima + violations do backend.

### Compose / docs

- README: como subir backend + frontend local.
- Opcional: serviço `frontend` no Compose servindo build estático via nginx; se aumentar complexidade demais, manter `npm run dev` documentado e Compose só na API/infra.

Decisão padrão: Compose continua com API/infra; frontend via `npm run dev` no README. Evita acoplar build Node ao Dockerfile da API nesta fase.

### Testes (PR 2)

- Backend: register/login, JWT obrigatório, ownership (outro userId → `403`), criar/cancelar autenticado.
- Frontend: smoke manual documentado no PR (cadastro → contratar → cancelar). Testes e2e automatizados ficam para depois, salvo se o harness Playwright do repo já existir (hoje não).

## Ordem de merge (GitFlow)

```text
develop
  └─ feature/api-contract-usability  → PR → develop
develop (atualizado)
  └─ feature/subscriber-portal       → PR → develop
push develop → workflow sync master
```

## Riscos e mitigação

| Risco | Mitigação |
|-------|-----------|
| Clientes atuais dependem de `404` na ativa vazia | Documentar breaking change no PR 1; comportamento novo é o desejado para UI |
| Secret JWT fraco em local | Default só em profile local; Compose/README exigem env em docker |
| Usuários criados só pela API antiga sem senha | Portal usa apenas `/auth/register`; criação legada continua para testes de lab |
| Escopo crescer (reativar, trocar plano) | Explicitamente fora; deixar estados visíveis sem ação |

## Critérios de aceite

### PR 1

- [ ] `GET active` retorna `204` sem assinatura elegível
- [ ] `GET .../subscriptions/{id}` funciona e casa com `Location`
- [ ] Histórico de user inexistente → `404`
- [ ] UUID inválido → `400`
- [ ] `GET /plans` lista os três planos com preço
- [ ] OpenAPI atualizado
- [ ] Testes novos + suíte existente OK

### PR 2

- [ ] Cadastro e login com JWT
- [ ] Landing de planos + painel único autenticado
- [ ] Contratar e cancelar pelo portal
- [ ] Empty state baseado em `204`
- [ ] Rotas de outro usuário bloqueadas
- [ ] README com passos locais
- [ ] CI verde no PR

## Não fazer

- Comentários no código explicando o óbvio ou autoria.
- Menções a assistência automatizada em commits, PRs ou docs.
- Escopo de billing real, reativação ou admin nesta leva.
