# Admin Panel UI Implementation Plan

> **For agentic workers:** implement task-by-task. Steps use checkbox syntax.

**Goal:** Painel admin polido no frontend Vite existente + deploy em `:9084`.

**Architecture:** Rotas `/admin/*` com shell próprio; nginx no container `web` faz proxy same-origin para `app:8080`.

**Tech Stack:** Vite, React 19, React Router 7, nginx Alpine, Docker Compose.

## Global Constraints

- Sem menções a IA/Cursor em commits, PRs ou artefatos versionados
- GitFlow: feature → develop → release → main/master
- Sem inventar invoices; só endpoints admin existentes
- Sem inline imports; switch exhaustivo em TypeScript

---

### Task 1: Visual system + tipos admin

- [ ] Atualizar `index.html` (fonts Syne + Manrope)
- [ ] Evoluir `styles.css` + `admin.css` (CSS variables ledger teal)
- [ ] Estender `types.ts` (paymentBehavior, OutboxEvent)

### Task 2: Shell e páginas admin

- [ ] Criar `admin/AdminLayout.tsx` e páginas Dashboard/Clientes/Assinaturas/Operacoes
- [ ] Atualizar `App.tsx` rotas; remover AdminPage monolítico de `pages.tsx`
- [ ] Login ADMIN → `/admin`; link Admin no portal

### Task 3: Deploy compose

- [ ] `frontend/Dockerfile` + `nginx.conf`
- [ ] Serviço `web` em `docker-compose.yml`
- [ ] Override portfolio: porta 9084 + CORS
- [ ] README URL pública

### Task 4: Hub + ship

- [ ] Atualizar card AssinaFlow em hub Pages
- [ ] Commit, PR → develop, merge/release conforme fluxo
- [ ] Deploy EC2 + smoke-test
