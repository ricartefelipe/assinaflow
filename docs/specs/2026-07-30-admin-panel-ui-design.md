# AssinaFlow Admin Panel UI — Design

**Data:** 2026-07-30  
**Decisão:** opção A — evoluir o frontend Vite/React existente.

## Objetivo

Painel administrativo polido (não restyle do Swagger) para operar assinaturas, clientes, cobrança simulada e outbox, integrado à API real. Portal do assinante permanece no mesmo app.

## Escopo

### Inclui
- Login contra `/api/v1/auth/login`; ADMIN redireciona para `/admin`
- Shell admin em `/admin/*` com navegação lateral
- Dashboard: KPIs derivados de users + subscriptions (ativas, cancelamento agendado, suspensas, créditos)
- Clientes: lista de usuários + edição de payment profile (`ALWAYS_APPROVE` / `ALWAYS_DECLINE` / `FAIL_NEXT_N`)
- Assinaturas: tabela com plano, status, ciclo, auto-renew, falhas
- Operações / Outbox: filtro por status, requeue de DEAD/PENDING
- Visual distintivo “ledger teal” (não purple/cream/broadsheet)
- Deploy público `http://54.94.163.136:9084` com nginx proxy `/api` → app `:8080`
- Atualização do card no hub Pages

### Fora de escopo
- Invoices (API não expõe)
- CRUD completo de planos/faturas
- Next.js separado

## Arquitetura

```
Browser :9084  →  nginx (web)  →  static SPA
                      └─ /api/*, /v3/*  →  Spring app :8080
```

Same-origin via proxy elimina CORS para a UI pública. CORS do backend inclui `:9084` como fallback.

## Rotas admin

| Rota | Função |
|------|--------|
| `/admin` | Dashboard |
| `/admin/clientes` | Usuários + payment profile |
| `/admin/assinaturas` | Lista de assinaturas |
| `/admin/operacoes` | Outbox + requeue |

## Visual

- **Direção:** ledger diurno — névoa sage, teal profundo `#0d5c4d`, ouro-cobre `#c4892a` para sinais monetários
- **Tipografia:** Syne (marca/display) + Manrope (UI)
- **Login:** brand-first (marca dominante, um subtítulo, formulário)
- **Admin:** sidebar fixa, tabelas densas, pills de status, motion sutil em hover/entrada

## Critérios de sucesso

1. Login `demo@assinaflow.test` abre dashboard admin
2. Telas clientes / assinaturas / operações carregam dados da API
3. URL pública `:9084` acessível; hub aponta para a UI
