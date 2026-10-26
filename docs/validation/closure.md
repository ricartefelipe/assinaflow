# Fechamento integrado — 26/10/2026

Implementação local concluída e validada: motor durável preservado, Stripe sandbox integrado à jornada, webhooks autenticados e gates locais aprovados. **Homologação externa Stripe não executada: credenciais de teste indisponíveis. NO-GO financeiro e produção mantidos.** Nenhuma cobrança real, envio real de mensagem, deploy ou publicação foi autorizado/executado.

Base preservada: branch `feature/melhorias-para-escalabilidade`, HEAD `c501c3a35547cc0f84f37e76bb4c0946e1eaf510`, sem upstream. Esta execução recebeu38 modificados/28 novos e preservou todos. Nenhuma operação Git de escrita, staging, ref ou PR. Estado completo em [files.md](files.md); protocolo e roteiro executável em [operations.md](operations.md).

## Alterações finais

Stripe foi definido nesta entrega, conforme instrução de fallback; escolher provedor não é mais pendência. `StripeApi`, `StripeCustomers`, `StripePaymentGateway`, `StripeWebhooks` e `StripeController` usam os endpoints oficiais de customer, Checkout setup, SetupIntent, PaymentMethod e PaymentIntent. A API é fixada em2025-02-24.acacia. Não há Stripe Subscriptions nem segundo motor de recorrência. O motor local continua responsável por contratação, renovação, upgrade, reativação, dunning e cancelamento.

Checkout hospedado cadastra método tokenizado; a volta do browser apenas consulta sessão/SetupIntent/PM no servidor e verifica o customer. Autenticação adicional usa Stripe.js com segredo efêmero somente ao titular e resposta no-store. PAN/CVC não são coletados ou persistidos pela aplicação. A conta e contratação no portal incluem cadastro/verificação/autenticação de pagamento. Aprovação ou redirecionamento do browser não concede acesso: o servidor consulta Stripe e aplica o domínio atomicamente.

Migration008 adiciona customer/método, operação Stripe, confirmações numeradas e inbox persistente. 001–007 preservadas. Obrigação, valor/BRL e identidade lógica seguem imutáveis; ciclos diferentes criam obrigações diferentes. Criação de intent tem chave estável; nova confirmação após recusa definitiva/autorização tem chave distinta e histórico, conservando o mesmo PaymentIntent. Timeout não vira recusa nem novo POST. Busca sem resultado é inconclusiva; janela Stripe v1 mínima24h é tratada conservadoramente (criação pendente23h), sem presumir retenção eterna de chave. Os limites locais48h não são anunciados como garantia Stripe.

Webhook valida bytes originais/HMAC, timestamp e modo de teste, deduplica ID/hash antes do ACK, processa com posse/tentativas limitadas e consulta Stripe fora de transação. Eventos antigos, duplicados e corrida com worker não desfazem aprovação. Reconciliação e webhook só consultam/aplicam resultado; **não iniciam PREPARED nem antecipam retry de recusa já aplicada**. O envio segue o motor/pedido autorizado. Resultado de dono antigo permanece protegido pelo fencing no banco.

Política preservada: cancelar antes da autorização impede envio; aprovação já autorizada mantém período pago/cancelamento agendado, sem reembolso inventado. Crédito excedente, prorrata limitada, fim de mês/bissexto, suspensão e reativação paga continuam cobertos. Outbox e efeito financeiro são recuperações distintas; reentrega não repete débito. Ownership/JWT/role/habilitação continuam ligados às rotas novas; ADMIN sem bypass do titular.

## Versões e comandos finais

Java host21.0.12.1 e Maven3.9.12; builder Maven3.9.16/Java21.0.12.1. Boot4.0.9/Spring7.0.10, springdoc3.0.3 e Testcontainers2.0.5. Foi a menor migração de framework necessária ao fix público dos advisories Spring; contratos REST preservados. Jackson2 usa o módulo de compatibilidade oficial, depreciado, junto ao Jackson3 gerenciado pelo Boot; não se declara migração completa para Jackson3.

Rabbit client5.37.0, HttpCore5.4.3, Plexus3.6.2 e Bouncy Castle1.85 corrigem componentes resolvidos. Transporte Docker de teste foi substituído pelo transporte oficial não sombreado3.7.1, via extensão pública Testcontainers/ServiceLoader. Conserva a função Docker e permite corrigir HttpCore; não foi apagado o componente necessário nem alterado metadado do JAR vulnerável para silenciar scanner. Core correspondente é dependência somente de teste. Estratégia não persiste configuração pessoal e foi validada com containers reais no Linux/Docker Desktop; outros hosts/registries privados não foram homologados.

Frontend host Node24.15.0, builder Node24.22.1/npm12.2.1; Router7.18.4, Vite8.3.5 e Playwright1.64.1. Undici6.29.1 substitui somente o patch interno npm, com SHA512 e verificação da faixa declarada (`^6.25.0`). PostgreSQL16.15 conserva major16; gosu1.19 foi recompilado de fonte oficial com hash pinado e Go1.27.3, mantendo entrypoint e troca de usuário. Redis/Rabbit preservam major; Python/pyc3.12.15 alinhados e bibliotecas OS corrigidas.

| Gate executado neste conjunto final | Comando / resultado |
|---|---|
| Backend completo | Em backend: `mvn -B verify -P integration-tests -Dtest.postgres.image=assinaflow-postgres:16-patched -Dtest.rabbitmq.image=assinaflow-rabbitmq:3-patched` — **99 testes, 0 falhas, 0 erros, 0 skips**, BUILD SUCCESS em26/10/2026 13:44:13 -03; 1min32s |
| Particularidades do host | Docker Desktop exige DOCKER_HOST do próprio host; Maven usou `-s /tmp/assinaflow-followup/settings-direct.xml` com Central direto, devido ao mirror lento. Não foram persistidas mudanças de configuração pessoal |
| Frontend por lockfile | `npm ci --ignore-scripts`, `npm run lint`, `npm run build`, `npm audit --omit=dev`, `npm audit` via npm12.2.1 — aprovados, zero advisories npm. Um warning preexistente Fast Refresh em AuthContext.tsx:58 |
| Stack construída completa | `python3 -u scripts/verify-stack.py` — PostgreSQL/Redis/RabbitMQ/API/Nginx, projeto/portas/volume próprios; **1 browser passou em5,9s (6,8s runner), zero retries/skips**, cleanup com exit0 |
| Processo e restore | `TEST_POSTGRES_IMAGE=assinaflow-postgres:16-patched python3 -u scripts/verify-isolated.py --frontend` — **1 browser passou em4,4s (6,5s runner), sem retries/skips**; JVM encerrada e recuperada somente por consulta, um efeito financeiro, dump/restore em banco vazio e leitura autenticada/cancelamento; exit0 |
| Upgrade | Teste integrado aplica001–006 com dados históricos e então007/008; usuário, assinatura, crédito, tentativa e outbox preservados |
| Broker | Teste assíncrono real fecha canal sem ACK, verifica redelivered/corpo, retoma listener e envia dez replays; um CHARGE/efeito de renovação além da contratação, sem novo débito |
| Auditorias | CycloneDX2.9.1 + Trivy0.76.1 para161 componentes runtime e218 completos; oito imagens realmente construídas, runtime/build separados, IDs conferidos atuais. Todos os gates HIGH/CRITICAL aprovados, sem supressões |
| Artefato e configurações | JAR contém adapter Stripe/migration008; nenhum ControlledGateway/SimulatedCrash/estratégia de teste empacotado. YAML CI parseado, scripts compilados em memória, guard do verificador rejeitou falta de credencial antes de rede |
| Git | Branch/HEAD/lista rastreada preservados, arquivos essenciais não ignorados, `git diff --check` aprovado |
| CI remota e sandbox externo | Não executados. CI aguarda versionamento/publicação pelo proprietário; sandbox real aguarda credenciais e entrega de eventos do ambiente autorizado |

As evidências anteriores não foram usadas para esconder gates finais: backend, frontend, browser, processos/restore e imagens alteradas foram reexecutados. Relatórios das imagens que não mudaram foram reutilizados **somente após comparar o ID imutável com a imagem atual**. As duas jornadas browser são ensaios diferentes, não retry de um mesmo erro. Não houve aumento de timeout para obter verde nesta execução.

## Evidências financeiras e limites

Os99 casos incluem os87 anteriores e12 contratos Stripe novos, com HTTP interceptado no teste e PostgreSQL real. Cobrem cadastro de customer/PM verificado, aprovação e renovação em dois intents, dez repetições sem outro intent, recusa HTTP402 e nova confirmação explícita no mesmo intent, processamento/auth adicional sem acesso, resposta de confirmação perdida e recuperação por GET, busca ausente até REVIEW, correlação de valor inválida sem acesso, chave live rejeitada, webhook inválido/expirado, duplicado/fora de ordem e corrida com recuperação. Dois casos verificam especificamente que consulta não envia obrigação preparada nem antecipa dunning. Isso é **contrato HTTP local**, não homologação da Stripe externa.

Os27 cenários de recuperação/conexões independentes continuam passando: crash controlado antes/depois de efeito remoto, sucesso remoto/rollback local, posse entre workers/consumidores, resposta obsoleta, janela/backoff/REVIEW, cancelamento antes/durante/incerto, falha de outbox sem repetir efeito e manutenção de crédito/calendário. Segurança tem JWT próprio e fluxos positivos/401/403 do titular/terceiro/admin, com habilitação/papel atuais no banco. Ganchos existem exclusivamente no teste; o reinício operacional usa uma JVM nova e fixture somente no banco descartável.

Falhas investigadas nesta execução: ambiguidade de import MediaType após atualização JUnit (compilação corrigida); relógio congelado com nanos arredondados pelo PostgreSQL deixava intenção alguns nanos no futuro (fixture passou à precisão de segundos, sem relaxar asserção); npm update no pacote global tentou resolver workspace de documentação não publicado (substituído pelo patch oficial isolado, verificado); transporte não sombreado exigiu declarar o core correspondente (corrigido). Scan do Boot novo revelou default Rabbit client antigo e, depois, crypto do core de teste; ambos foram corrigidos e reanalisados. Esses resultados intermediários não foram apresentados como gates verdes.

## Segurança final

Correções: Spring6.2.19 com dois advisories críticos do scanner substituído por7.0.10 suportado; Rabbit client mantido5.37; Node/npm e patch Undici eliminam os altos de ferramenta; Plexus/HttpCore/Bouncy Castle corrigidos; PostgreSQL mantém16 e troca o helper compilado com Go vulnerável por mesma fonte/Go corrigido; Redis/Rabbit/Brotli corrigidos. Não houve supressão, redução de severidade, lib necessária removida sem substituição ou aceite de risco.

Ferramentas/base: Trivy0.76.1, DB versão2 atualizada25/10/2026 19:10:35 UTC e Java DB versão1 em25/10/2026 00:59:20 UTC; metadados exatos e SHA256 em [security-results.json](security-results.json). Preparação antiga do Dependency-Check12.1.0 não concluiu; **não** é gate aprovado. Alternativa completa CycloneDX/Trivy concluiu. Trivy alerta sobre hashes secundários SHA-384/SHA3 não suportados; PURLs/grafo e SHA256 foram preservados. Nenhuma ausência de vulnerabilidade foi inferida de scan interrompido.

| Artefato final | Critical | High | Medium | Low | Unknown |
|---|---:|---:|---:|---:|---:|
| Java runtime (161 componentes) | 0 | 0 | 0 | 0 | 0 |
| Java completo/testes (218 componentes) | 0 | 0 | 1 | 0 | 0 |
| Backend/JRE runtime | 0 | 0 | 54 | 4 | 0 |
| Frontend/Nginx runtime | 0 | 0 | 0 | 0 | 0 |
| PostgreSQL16/gosu runtime | 0 | 0 | 1 | 0 | 1 |
| Redis7 runtime | 0 | 0 | 0 | 0 | 0 |
| RabbitMQ3 runtime | 0 | 0 | 4 | 3 | 0 |
| Maven build | 0 | 0 | 99 | 47 | 0 |
| Node/npm build | 0 | 0 | 1 | 0 | 0 |
| Go/gosu build | 0 | 0 | 1 | 0 | 1 |

Contagens são ocorrências por componente/caminho, não CVEs únicas. IDs, versões instaladas/corrigidas e fontes de **todos** os residuais estão no JSON. Runtime Java mantém pacotes OS médios/baixos; não se afirma ausência de alcance em cada CVE. Build não é runtime: npm/Node e o cache/plugins Maven não são copiados integralmente para os serviços finais. Builds não receberam chaves Stripe e a CI mantém permissões read-only.

Residuais específicos: HttpClient5.5.2 (CVE-2026-64607, fix5.6.3) no teste/build, sem uso pelo adapter Stripe JDK; ip-address10.7.2 (CVE-2026-111997, fix10.7.4) em npm, sem código servido no SPA; moby/sys/user0.1.0 (CVE-2026-61801, fix0.4.1), DoS se receber arquivo de usuários/grupos malicioso — o entrypoint usa arquivos da imagem e usuário fixo, não montar /etc/passwd/group não confiáveis. x/sys0.1.0 registra UNKNOWN/CVE-2026-39824 para NewNTUnicodeString/Windows, fix0.44.0; a plataforma é Linux, mas o achado permanece no relatório sem supressão. Rabbit mantém BusyBox/ssl_client e xz médios/baixos; restringir rede/management e entradas não confiáveis. Não se iniciou perseguição indefinida de Medium/Low; nenhum risco foi aceito em nome do proprietário.

Scanner não certifica suporte de todos os binários/alcance de CVEs, cobertura completa Erlang, vulnerabilidades desconhecidas, secrets/histórico, pentest ou licenças. TLS/rede/retensão/auditoria administrativa, suporte operacional, RPO/RTO e broker de cliente não foram homologados para produção. SMTP/TotalRecall externos não foram acionados. Trial, invoice fiscal, chargeback/refund, catálogo novo e funcionalidades comerciais opcionais continuam fora da entrega.

## Identificação das imagens finais

As tags são locais, sem push. IDs completos abaixo, plataforma e bases no JSON; não confundir tag mutável com prova de identidade.

| Referência local | Identidade imutável conferida | Plataforma |
|---|---|---|
| `assinaflow-validation-stripe-backend:local` | `sha256:3b024b666ea629bae1452695d6f024f5a8501c354235ad6e019e16fa80721f69` | linux/amd64 |
| `assinaflow-validation-stripe-frontend:local` | `sha256:efbd6c7f7ce8b6a746f5e5f186e2d4463e9e7f7b01a6082220ae7f1ac17926b2` | linux/amd64 |
| `assinaflow-postgres:16-patched` | `sha256:8631067c508855feb03165145449c2271fef70e2281aeb03ee635929a467abe3` | linux/amd64 |
| `assinaflow-redis:7-patched` | `sha256:19ce022db444d5e54bc40590e38e282b0f17aad5abab66adf54ed96e049363de` | linux/amd64 |
| `assinaflow-rabbitmq:3-patched` | `sha256:378a5e75b2cb7032d453bb82b5298bf3ddfe4580ca97229853d2403c9135d663` | linux/amd64 |
| `assinaflow-validation-stripe-maven-build:local` | `sha256:51b78f50118edb092c64c5545250d6dd58546328d1cb4f628d35f3ef18c20bc1` | linux/amd64 |
| `assinaflow-validation-stripe-node-build:local` | `sha256:2e35747a402dcb6ce78552218056976ed5f47cdd08dbe638f15b81431f23aad9` | linux/amd64 |
| `assinaflow-validation-stripe-gosu-build:local` | `sha256:ed022647e7a28f509cb13f197e6013a2fced60993ae9ce8d9b3e9a878bc6d2bc` | linux/amd64 |

## Dependência externa e ações do proprietário

A implementação e os gates locais estão concluídos. A única dependência externa para executar a homologação financeira restante é disponibilizar as três credenciais do mesmo sandbox Stripe e seu canal autenticado de eventos, sem gravar valores em comandos/logs/relatórios. O roteiro completo e o verificador por fases estão no manual; não houve tráfego financeiro externo nesta execução. Produção/chave live continuam bloqueadas e não recebem GO por aprovação local.

Checklist única:

- [ ] Revisar e versionar todo o inventário, inclusive migrations, recursos de teste/ServiceLoader e script de manutenção npm; Git permanece sob seu controle.
- [ ] Executar CI remota sobre o commit publicado; os gates continuam obrigatórios HIGH/CRITICAL.
- [ ] Disponibilizar credenciais Stripe sandbox e executar/acompanhar o roteiro externo de aprovação, recusa, autenticação, renovação, eventos, replay e cancelamento; Stripe já está definido, não há escolha indefinida de provedor.
- [ ] Decidir tratamento dos residuais/limites operacionais antes de qualquer piloto ou produção. Cobrança real exige homologação comprovada e autorização explícita distinta.
