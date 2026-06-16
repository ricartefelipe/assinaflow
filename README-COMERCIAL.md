# AssinaFlow — Starter Kit de Cobrança Recorrente em Spring Boot

> Base de código pronta para produção para o ciclo de cobrança recorrente: **outbox transactional, idempotência, retry determinístico e suspensão automática**. Você integra em dias, não em semanas.
>
> Documentação técnica completa: [`README.md`](README.md).

---

## Que problema resolve

Cobrança recorrente parece simples — até a primeira cobrança duplicada cair no cartão do cliente. As partes que dão errado quase nunca são o pagamento em si, e sim:

- **Cobrança duplicada** quando duas instâncias rodam o job de renovação ao mesmo tempo.
- **Evento perdido**: o pagamento foi confirmado, mas o resto do sistema não soube.
- **Retry mal feito**: quantas vezes tentar, com qual intervalo, quando suspender.
- **Semanas gastas no básico** em vez de entregar o produto.

O AssinaFlow entrega exatamente essa camada de confiabilidade, do jeito que se coloca em produção.

---

## O que vem no kit

| # | Recurso | Descrição |
|---|---------|-----------|
| 1 | Renovação automática | Job diário em UTC que renova no dia certo e move o ciclo com segurança; cancelamento agendado não corta acesso antes do fim do período pago. |
| 2 | Retry determinístico | 1ª falha em +15min, 2ª em +60min, 3ª falha suspende a assinatura e desliga a renovação; cada tentativa registrada para auditoria. |
| 3 | Concorrência multi-instância | Row lock com `SKIP LOCKED` no PostgreSQL: nenhuma renovação é processada duas vezes, com qualquer número de instâncias. |
| 4 | Outbox + idempotência | Eventos persistidos na mesma transação, publicados com retry e backoff, marcados como `DEAD` após o limite; consumidor idempotente por constraint única. |
| 5 | Observabilidade | Métricas Prometheus (`payment_charge_total`, `renewal_attempt_total`, `outbox_pending`, `outbox_dead`) e logs correlacionados por `X-Request-Id`. |
| 6 | Testado e documentado | JUnit 5, Mockito e Testcontainers; OpenAPI/Swagger; Docker Compose; README com as regras de negócio explícitas. |

**Stack:** Java 21 · Spring Boot 3 · PostgreSQL 16 · Liquibase · Redis · RabbitMQ · Testcontainers · Prometheus · Docker Compose.

---

## Para quem é

- Devs sêniores e tech leads que precisam de cobrança recorrente confiável sem reinventar a roda.
- Startups e micro-SaaS que querem controle do próprio ciclo de cobrança, não dependência total do gateway.
- Times que precisam de uma referência sólida de outbox, idempotência e retry em Spring Boot.

**Não é para:** quem busca um SaaS de billing gerenciado (aqui você tem o código, com controle total), nem para quem não pretende manter código Java/Spring.

---

## Começando (3 passos)

```bash
docker compose up --build
```

Sobe a aplicação (perfil `docker`), PostgreSQL, Redis e RabbitMQ. Em seguida:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Métricas: `http://localhost:8080/actuator/prometheus`

Passo a passo completo, perfis, regras de banco e testes: [`README.md`](README.md).

---

## Licença

Produto comercial licenciado — **não** é open source. Resumo:

- ✅ Usar, modificar e incorporar em produtos **próprios e de clientes**, sem limite de produtos finais.
- ✅ Implantar os produtos finais em produção.
- ❌ Revender/redistribuir o código como template, boilerplate ou produto concorrente.
- ❌ Publicar o código-fonte como base reutilizável aberta.

Termos completos em [`LICENSE`](LICENSE).

---

## Implantação assistida (opcional)

A licença é entregue "as-is" com documentação. Se quiser apoio para colocar em produção, há um pacote separado de **Implantação Assistida**: onboarding técnico, adaptação ao seu domínio e suporte a dúvidas por período definido.

Contato: **felipericartem@gmail.com**

---

## Autor

**Felipe Ricarte Magalhães** — Senior Backend Engineer · Software Architect (hands-on). Mais de 17 anos em tecnologia, com foco em backend e arquitetura aplicada: APIs, integrações, mensageria, multitenancy e observabilidade em ambientes corporativos.
