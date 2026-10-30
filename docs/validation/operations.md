# Operação e homologação de pagamentos

## Modalidades

`APP_BILLING_MODE=demo` / `APP_PAYMENTS_GATEWAY=simulated` usa dados sintéticos e o simulador persistente. `APP_BILLING_MODE=sandbox` / `APP_PAYMENTS_GATEWAY=stripe` usa a API Stripe com chave `sk_test_`, publishable key `pk_test_` e webhook secret `whsec_`. Autenticação e JWT dedicado são obrigatórios. `production` e chaves live continuam rejeitados. O transporte HTTP genérico legado não tem recuperação e não pode iniciar o serviço financeiro.

Stripe foi definido para esta entrega. O motor local controla contratação, renovação, upgrade e reativação; só usa PaymentIntents. **Não criar Stripe Subscriptions, cobranças automáticas de catálogo ou outro motor de recorrência para as mesmas obrigações.** Não há PAN/CVC no banco, no backend ou em formulários da aplicação. O cadastro ocorre no Checkout hospedado; autenticação adicional usa o componente Stripe.js e o mesmo PaymentIntent. O browser nunca confirma acesso local.

## Instalação e gates locais

Java21, Maven3.9+, Node24 e Docker Compose com suporte a `!override`/`!reset`. Build Java usa Maven3.9.16. Framework Boot4.0.9/Spring7.0.10, springdoc3.0.3 e Testcontainers2.0.5. Jackson2 permanece pelo módulo de compatibilidade oficial para preservar os contratos existentes e bibliotecas JWT; é um módulo depreciado, não uma migração completa para Jackson3. Rabbit client5.37 é manutenção compatível exigida pela auditoria. Bouncy Castle1.85 no escopo de teste, HttpCore5.4.3 e Plexus3.6.2 corrigem bibliotecas de teste/plugins sem trocar sua família de API. O transporte Docker oficial não sombreado substitui o JAR que embutia HttpCore vulnerável; uma estratégia em src/test, registrada por ServiceLoader, conserva a comunicação com Docker e não grava configuração no diretório pessoal. A suíte completa valida PostgreSQL/Rabbit/limpeza real com esse transporte em Linux/Docker Desktop; registries privados e outros hosts não foram homologados.

Na raiz:

```sh
docker build -f deploy/images/postgres.Dockerfile -t assinaflow-postgres:16-patched deploy/images
docker build -f deploy/images/redis.Dockerfile -t assinaflow-redis:7-patched deploy/images
docker build -f deploy/images/rabbitmq.Dockerfile -t assinaflow-rabbitmq:3-patched deploy/images
cd backend
mvn -B verify -P integration-tests -Dtest.postgres.image=assinaflow-postgres:16-patched -Dtest.rabbitmq.image=assinaflow-rabbitmq:3-patched
```

No Docker Desktop local foi necessário definir seu socket em `DOCKER_HOST`; não copiar socket/endereço de outra instalação. O POM define os plugins estáveis necessários; testes sem profile integration-tests não substituem o gate completo. `StripeContractIntegrationTest` intercepta HTTP no próprio teste e usa credenciais sintéticas, não acessa a Stripe externa.

Frontend: `cd frontend && npm ci --ignore-scripts && npm run lint && npm run build && npm audit --omit=dev && npm audit`. Container/CI usam npm12.2.1 e a manutenção `patch-npm-undici.cjs`: aplica undici6.29.1 oficial com SHA512 e verifica que pertence à faixa declarada pelo node-gyp (`^6.25.0`). Não remove a dependência nem muda sua major. O update genérico dentro do npm empacotado falha porque tenta resolver workspace de documentação não publicado; o script troca somente o pacote necessário pelo patch oficial. O build funcional e o scanner verificam o resultado. Nenhum script de instalação do projeto é autorizado automaticamente.

Após empacotar: `TEST_POSTGRES_IMAGE=assinaflow-postgres:16-patched python3 -u scripts/verify-isolated.py --frontend`. Instalar Chromium antes com `cd frontend && npx playwright install chromium`; na CI usar `--with-deps`. Cria banco/container próprios, API autenticada, browser sem retries, intenção/sucesso remoto sintéticos, reinício real da JVM somente com recuperação habilitada e efeito único. Faz dump, restore em banco vazio, novo restart e leitura autenticada/cancelamento. Só encerra o container próprio; nunca toca volume existente. SQL de fixture é restrito ao banco recém-criado; não há gancho de crash em produção.

Para validar a stack completa, construir `assinaflow-validation-stripe-backend:local` a partir de backend e `assinaflow-validation-stripe-frontend:local` a partir de frontend, além das três imagens de infraestrutura acima; executar `python3 -u scripts/verify-stack.py`. Cria projeto Compose aleatório, portas locais e volume próprios, verifica health com Redis/Rabbit/PostgreSQL reais e browser contra o Nginx construído. Encerra somente esse projeto e seu volume. O nome do artefato de validação não altera o modo: este ensaio é explicitamente demo/simulated. Não executar `down -v` contra um projeto de cliente.

## Contrato Stripe e persistência

Referências oficiais: [PaymentIntents](https://docs.stripe.com/api/payment_intents), [confirmação](https://docs.stripe.com/api/payment_intents/confirm), [Checkout setup](https://docs.stripe.com/api/checkout/sessions/create), [idempotência](https://docs.stripe.com/api/idempotent_requests), [consistência de busca](https://docs.stripe.com/api/payment_intents/search) e [webhooks](https://docs.stripe.com/events/manage-webhook-endpoints). Requisições fixam `Stripe-Version: 2025-02-24.acacia`, versão pública documentada pela Stripe; configurar o endpoint de eventos na mesma versão. Não usar o default variável da conta como contrato implícito.

A migration008 é aditiva sobre007; 001–007 não foram reescritas. Guarda customer e payment method por usuário, operação Stripe por obrigação com intent único, confirmações numeradas/chaves/método tokenizado e inbox de eventos com hash, posse temporal e tentativas. Não guarda client secret nem payload completo de webhook. O `clientSecret` necessário à autenticação é consultado sob demanda e retornado somente ao titular via POST com `Cache-Control: no-store`.

Fluxo:

1. Titular chama `POST /api/v1/users/{id}/billing/setup`. Cliente Stripe é criado com chave persistente derivada do usuário; o servidor grava a referência. Intenção de cadastro com resposta perdida por mais de23h exige revisão, não recriação cega.
2. Checkout `mode=setup` recebe customer do servidor e URLs fixas da configuração. Retorna para a conta. `GET .../billing/status` consulta a sessão armazenada, SetupIntent succeeded e PaymentMethod anexado ao customer correto antes de marcar cadastro concluído. Não aceita customer/método enviados pelo browser.
3. Contratação/upgrade/reativação/renovação usam os serviços existentes. Obrigação é confirmada antes da rede. Stripe operation é persistida antes de criar o PaymentIntent sem confirmar; customer, valor, BRL e metadata usuário/obrigação são verificados.
4. Confirma o mesmo PaymentIntent com payment method tokenizado e `off_session=true`. Chave de criação estável `obligation-UUID-intent`; cada nova confirmação autorizada após **recusa definitiva** tem chave própria e registro durável. Repetição da execução, nova confirmação autorizada e novo ciclo são conceitos distintos. Recusa pode usar um método novo cadastrado, sem mudar obrigação/preço ou criar outro PaymentIntent.
5. Timeout/erro não conclusivo consulta o intent conhecido. Sem referência, busca metadata; ausência ou atraso de busca não prova ausência de efeito. Não repete criação/confirm após resultado desconhecido. Chaves v1 Stripe podem ser expurgadas após24h; criação pendente tem limite conservador23h. O ledger mantém sua janela de48h para envio/reatorização local, mas isso **não** é garantia de retenção Stripe; não autoriza recriar intent depois do prazo. Intent conhecido é consultável além da janela.
6. `processing`, `requires_action`, autenticação necessária e ausência inconclusiva ficam pendentes. O titular acessa a conta e usa “Verificar ou autenticar pagamento”; Stripe.js confirma o mesmo intent no componente hospedado. O servidor consulta novamente e aplica aprovação. `requires_payment_method` sem erro conclusivo não é recusa fictícia. Recusa HTTP402 com intent é correlacionada; chaves novas só após recusa definitiva/autorização do motor.
7. Webhook autentica HMAC-SHA256 sobre bytes originais e timestamp, tolerância300s e comparação constante. Limite256KiB, rejeição live, dedupe persistente de event ID/hash. Recebimento persiste antes do ACK; worker com lease e limite de tentativas consulta o provedor fora de transação, nunca aplica o status do payload. Evento antigo/duplicado não desfaz aprovação, e a posse do ledger protege corrida com recuperação. Inbox REVIEW é pendência operacional, não sucesso presumido.

API: GET público `/api/v1/billing/config` identifica explicitamente demo ou Stripe sandbox e expõe somente publishable key. Cadastro/status e ações em `/users/{id}` exigem titular; ADMIN não tem bypass. `POST .../payments/{paymentId}/stripe/action` entrega ação apenas da obrigação do titular. `POST .../stripe/reconcile` solicita somente consulta/aplicação de resultado conhecido, não autorização de cobrança. PREPARED e recusa já aplicada não são reenviados por esse caminho; o job/pedido explícito continua responsável por envios e dunning. Webhook público `POST /api/v1/billing/stripe/webhook` exige autenticação própria Stripe, não JWT.

## Obrigações, posse e regras de domínio

`payment_obligations` congela identidade lógica, chave, titular, valor, moeda, contexto/preço/ciclo e janela; `payment_executions` registra CHARGE/QUERY/APPLY. Constraints garantem unicidade de ciclo/chaves e uma obrigação aberta por usuário. Lock de usuário/assinatura/obrigação, lease120s, fencing por UUID e transações curtas serializam instâncias. Rede não mantém locks de domínio; resultado conhecido é gravado antes da aplicação atômica de assinatura, histórico, crédito, ledger e outbox.

Identidades: renovação por assinatura/ciclo, contratação por usuário/plano/início/geração, alteração por assinatura/plano/data/versão e reativação por assinatura/limite anterior/novo início. Replays não alteram valor; nova contratação após encerramento não reutiliza aprovação antiga. Consumidor só aceita obrigação reservada pelo servidor com titular/ciclo/valor correspondentes. Métricas de CHARGE não contam consultas como novos débitos.

Estados PREPARED, IN_FLIGHT, UNKNOWN, APPROVED, DECLINED, REVIEW e CANCELED. Aprovação conhecida pode estar pendente de aplicação. Lease expirado e UNKNOWN consultam, não reenviam por presumir crash anterior ao envio. Recuperação seleciona até200 itens; job padrão100 a cada15s, habilitável independentemente do scheduler geral. Seis resultados inconclusivos têm backoff15/30/60/120/240/480s e conduzem a REVIEW. Webhook autenticado pode solicitar nova consulta de revisão inconclusiva; não contorna correlação inválida, janela de envio ou posse vigente. Aprovação encerrada não reabre.

Recusas de renovação seguem15/60 minutos e terceira suspensão; recusa inicial/plano/reativação só reautoriza por pedido explícito dentro da janela. Retomar cancelamento ainda não enviado pode reautorizar a mesma obrigação cancelada, mantendo chave/valor. Não existe API para presumir aprovação, copiar obrigação para contornar REVIEW ou inventar reembolso. Fila ADMIN: `GET /api/v1/admin/payments/review`; titular: coleção/detalhe em `/users/{id}/payments`, até100 itens.

Cancelamento antes da autorização impede envio. Após autorização, aprovação mantém o período recém-pago, CANCELAMENTO_AGENDADO e autoRenew=false, sem reembolso automático. Finalização espera resultado incerto, aprovação não aplicada ou REVIEW. Uma recusa após cancelamento não autoriza novo débito.

UTC e período [início, expiração): 31/01/2025→28/02→28/03; bissexto31/01/2024→29/02. Não preserva âncora31. Reativação vencida paga e abre período desde hoje. Upgrade cobra diferença proporcional limitada ao ciclo; downgrade credita próxima renovação; crédito excedente é carregado. Não há trial, grace configurável, impostos, invoice fiscal, refund/chargeback, moeda adicional ou catálogo versionado.

Outbox tem entrega pelo menos uma vez, não exactly-once. Falha/reentrega não cria obrigação ou efeito financeiro novo. A recuperação financeira independe do broker; recuperar publicação não substitui reconciliar o provedor. SMTP não foi homologado nem acionado; logging omite e-mail/corpo. Não atribuir garantia de entrega única a mensagens/e-mails.

## Sandbox real — roteiro executável

Ainda não executado externamente: faltam as três credenciais Stripe do mesmo sandbox. Não colocar valores em comando registrado, arquivo ou relatório. Usar ambiente/gerenciador de segredos; sem shell trace. Variáveis necessárias: STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY, STRIPE_WEBHOOK_SECRET; JWT_SECRET dedicado e STRIPE_RETURN_URL (URL da conta no portal, default http://localhost:5173/conta). Banco descartável separado de qualquer instalação existente.

1. Criar container PostgreSQL corrigido com nome `assinaflow-stripe-proof-<identificador>`, banco/usuário synthetic e sem volume existente. Usar porta local livre e credenciais sintéticas somente desse banco; configurar DB_URL/USERNAME/PASSWORD no backend. Iniciar o backend com APP_BILLING_MODE=sandbox, APP_PAYMENTS_GATEWAY=stripe, autenticação ligada e logging como sender; portal na porta5173 com proxy para a API local. Nenhum serviço de produção deve ser apontado.
Preparação reproduzível, em shell sem trace e com as três credenciais já carregadas pelo canal seguro (não digitar valores em argumentos):

```sh
export SANDBOX_DB_CONTAINER="assinaflow-stripe-proof-$(date +%s)"
docker run --rm -d --name "$SANDBOX_DB_CONTAINER" --label assinaflow.proof=stripe-sandbox -p 127.0.0.1::5432 -e POSTGRES_DB=stripeproof -e POSTGRES_USER=proof -e POSTGRES_PASSWORD=synthetic-proof-only assinaflow-postgres:16-patched
export DB_URL="jdbc:postgresql://127.0.0.1:$(docker port "$SANDBOX_DB_CONTAINER" 5432/tcp | sed 's/.*://')/stripeproof"
export DB_USERNAME=proof DB_PASSWORD=synthetic-proof-only
export JWT_SECRET="$(openssl rand -hex 32)"
export APP_BILLING_MODE=sandbox APP_PAYMENTS_GATEWAY=stripe APP_SECURITY_ENABLED=true
export APP_NOTIFICATIONS_SENDER=logging APP_PAYMENTS_ASYNC_ENABLED=false
export APP_RENEWAL_CRON='*/5 * * * * *'
export STRIPE_RETURN_URL=http://localhost:5173/conta SANDBOX_API_URL=http://127.0.0.1:8080
# Em terminais separados, preservando o ambiente necessário sem imprimir segredos:
(cd backend && java -jar target/assinaflow-1.0.2.jar)
(cd frontend && E2E_API_URL=http://127.0.0.1:8080 npm run dev -- --host 127.0.0.1 --port 5173)
```

A senha acima é somente fixture sintética do container próprio. As chaves Stripe e JWT não são passadas como argumento nem gravadas. Confirmar prontidão com pg_isready e /actuator/health; se a porta8080/5173 estiver ocupada, escolher portas locais livres e ajustar retorno/proxy/verificador. Ao finalizar, encerrar os processos próprios e `docker stop "$SANDBOX_DB_CONTAINER"` somente nesse container; --rm remove seu volume anônimo, nenhum volume preexistente foi montado.

2. Para eventos locais, usar Stripe CLI autenticada **somente na conta de teste**, encaminhando ao webhook local; manter sua chave no ambiente STRIPE_API_KEY, não em argumento de processo. Obter o signing secret no gerenciador seguro/terminal privado e configurar o backend; não salvar saída que contenha whsec. Se usar endpoint HTTPS de sandbox, configurar assinatura e versão da API na Workbench. Não considerar eventos simulados por HTTP local como entrega autenticada da Stripe externa.
3. Criar conta sintética `sandbox-...@example.test` no portal. Cadastrar método pela página hospedada; usar exclusivamente cenários oficiais de [teste Stripe](https://docs.stripe.com/testing), sem copiar PAN/CVC para scripts, logs ou banco. Validar cadastro/aprovação, método que permita cadastro mas recuse cobrança posterior e método que exija autenticação adicional em contas sintéticas separadas.
4. Definir SANDBOX_EMAIL/SANDBOX_PASSWORD pelo ambiente para o verificador da conta correspondente. Com API local ativa, executar:

```sh
python3 scripts/verify-stripe-sandbox.py --base http://127.0.0.1:8080 --phase approve
python3 scripts/verify-stripe-sandbox.py --base http://127.0.0.1:8080 --phase replay
python3 scripts/verify-stripe-sandbox.py --base http://127.0.0.1:8080 --phase decline
python3 scripts/verify-stripe-sandbox.py --base http://127.0.0.1:8080 --phase action
```

Códigos de saída do verificador: `0` aprovado; `1` falha; `2` configuração (variáveis ausentes ou chave que não é `sk_test_`), antes de qualquer rede; `3` inconclusivo (sem aprovação na janela ou busca Stripe ainda sem resultado) — consultar de novo, nunca reenviar. A documentação oficial de idempotência v1 ([idempotent_requests](https://docs.stripe.com/api/idempotent_requests)) confirma o modelo adotado: chaves podem ser expurgadas após 24 h (por isso o limite conservador de 23 h), a mesma chave com parâmetros diferentes é recusada, a resposta gravada — inclusive `500` — é devolvida na repetição e só há gravação depois que a execução do endpoint começa.

```bash
```

`approve` usa conta sem assinatura e método aprovado; `decline` outra conta sem assinatura e método de recusa; `action` outra conta e conclusão manual no componente do provedor. Esperados201/400/503 respectivamente, nenhuma assinatura ativa na recusa ou antes da autenticação, depois confirmação do servidor. Verificador exige app Stripe sandbox/loopback, conta @example.test, chave de teste e correlação remota; não imprime tokens/client secrets/corpos de erro. Busca eventualmente consistente pode impedir concluir dentro do prazo; isso é evidência inconclusiva, não prova de ausência de débito. Não repetir criação de cobrança como diagnóstico.

5. Renovação: com a conta aprovada deste roteiro, executar `python3 scripts/verify-stripe-sandbox.py --phase prepare-renewal` e depois `python3 scripts/verify-stripe-sandbox.py --phase renewal`. A preparação exige container de prefixo próprio e label de sandbox, sem bind mounts, e somente pagamentos aprovados/aplicados da conta sintética autenticada. Ajusta exclusivamente sua assinatura para um ciclo vencido, incrementando a versão; não usar banco preexistente. O scheduler de cinco segundos acima deverá criar outra obrigação/intent e uma única aprovação; se o processamento já tiver ocorrido, o verificador aceita a evidência persistente correspondente. Webhooks reais devem aparecer na inbox como DONE, correlacionados ao lookup; payloads/chaves não entram no relatório.
6. Executar `python3 scripts/verify-stripe-sandbox.py --phase cancel`; esperado cancelamento agendado, autoRenew=false, mesmo período pago e nenhum PaymentIntent adicional. Consultar também no dashboard sandbox do provedor para conferir as referências, sem exportar segredos. Descartar somente os recursos sintéticos próprios conforme política da conta de teste.

Perda de resposta/crash são injetados exclusivamente nos testes locais controlados; não provocar falha na Stripe. Não há homologação externa certificada por esta documentação. Cobrança real/produção dependem de execução comprovada e autorização expressa distinta.

## Upgrade, restore, segurança e auditorias

Parar workers e obter backup autorizado antes de atualizar instalação. Restaurar em banco vazio, aplicar migrations preservadas e ler pela API. Teste de upgrade carrega001–006 com usuário/assinatura/crédito/tentativa/outbox anteriores e aplica007/008 verificando preservação. Nunca inferir pagamentos externos históricos ausentes do ledger.

`pg_dump -Fc` e `pg_restore --exit-on-error` em banco vazio; guardar ledger, operações Stripe, inbox, execuções e outbox juntos. Depois do restore, reconciliar referências com Stripe antes de liberar novos envios; backup local não desfaz efeito remoto. Ensaio sintético restaura também efeito do simulador por ser local. RPO/RTO de cliente e recuperação de disco/cluster broker não foram medidos.

PostgreSQL permanece na major16. O Dockerfile recompila a fonte oficial gosu1.19, com SHA256 pinado, em Go1.27.3 corrigido e conserva o entrypoint/função de troca de usuário; não conecta imagem de major nova a volume antigo. Build e runtime são auditados separadamente. Não foi removido componente necessário para limpar scanner.

JWT mantém assinatura, issuer/audience e validação de habilitação/expiração/papel no banco. Não há refresh/revogação individual, recuperação de senha ou auditoria administrativa completa. Não há usuário/senha fallback gerado pelo framework. Métricas/OpenAPI continuam públicas na demonstração; instalações reais exigem TLS, rede/management restritos e política de dados definida. Redis/SMTP/TotalRecall externos e capacidade/SLO não foram homologados para produção.

Gerar SBOM com CycloneDX2.9.1 nos escopos runtime e incluindo testes; analisar com Trivy0.76.1. Analisar imagens realmente construídas e estágios Maven/Node/gosu por docker save + trivy image --input, sem supressões. A CI reproduz testes, browser/restore/stack e auditorias bloqueantes HIGH/CRITICAL. Versões, plataformas, bases, hashes e achados finais constam de [closure.md](closure.md) e [security-results.json](security-results.json). CI remota não foi acionada.
