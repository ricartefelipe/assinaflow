# Reconciliação de arquivos — fechamento integrado de26/10/2026

Branch `feature/melhorias-para-escalabilidade`, HEAD `c501c3a35547cc0f84f37e76bb4c0946e1eaf510`, sem upstream. Nenhuma operação Git de escrita foi executada; staging, commit, push e publicação permanecem sob controle do proprietário.

Estado recebido nesta execução:38 modificados e28 novos não ignorados. Todos os66 caminhos permanecem presentes. Estado final: **156 rastreados, 42 modificados, 43 novos não ignorados, 0 excluídos, 0 staged e 1271 ignorados de execução**. A lista rastreada é idêntica à inicial. Preservadas também as alterações da primeira rodada (23 modificados/11 novos).

Nenhum arquivo essencial desapareceu. Sem symlinks/submodules de fonte identificados. Ignorados estão exclusivamente em backend/target, frontend/node_modules, frontend/dist e frontend/test-results; são artefatos, não fonte para staging. Não foram apagados ignorados preexistentes, instalações Chromium antigas ou outros checkouts. Inventários e logs transitórios estão em /tmp/assinaflow-followup e /tmp/assinaflow-review; evidências estáveis em closure.md/security-results.json.

## Modificados

- `.github/workflows/ci.yml`
- `README-COMERCIAL.md`
- `README.md`
- `backend/Dockerfile`
- `backend/pom.xml`
- `backend/src/main/java/br/com/ricarte/assinaflow/AssinaFlowApplication.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/auth/JwtAuthenticationFilter.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/auth/JwtService.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/config/CacheConfig.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/config/SchedulingConfig.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/config/SecurityConfig.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/notification/LoggingNotificationSender.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/notification/NotificationService.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/HttpPaymentGateway.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/PaymentChargeCommand.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/PaymentConfig.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/PaymentGateway.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/SimulatedPaymentGateway.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/subscription/PaymentChargeConsumer.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/subscription/PaymentResult.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/subscription/PaymentService.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/subscription/ProrationService.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/subscription/RenewalService.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/subscription/SubscriptionRepository.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/subscription/SubscriptionService.java`
- `backend/src/main/resources/application-docker.yml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- `backend/src/test/java/br/com/ricarte/assinaflow/AsyncPaymentsIntegrationTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/IntegrationTestBase.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/SubscriptionFlowIntegrationTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/subscription/ProrationServiceTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/subscription/RenewalServiceOutboxReclaimTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/subscription/SubscriptionServiceTest.java`
- `docker-compose.yml`
- `frontend/Dockerfile`
- `frontend/e2e/smoke.spec.ts`
- `frontend/package-lock.json`
- `frontend/playwright.config.ts`
- `frontend/src/api/client.ts`
- `frontend/src/pages.tsx`
- `frontend/vite.config.ts`

## Novos — conjunto completo para revisão

- `backend/.dockerignore`
- `backend/src/main/java/br/com/ricarte/assinaflow/config/BillingModeGuard.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/BillingConfigController.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/PaymentContext.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/PaymentController.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/PaymentLedger.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/PaymentObligation.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/PaymentOutcomeUnknownException.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/PaymentRecoveryScheduler.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/PaymentReviewController.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/PaymentSettlement.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/ProviderPayment.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/stripe/StripeApi.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/stripe/StripeController.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/stripe/StripeCustomers.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/stripe/StripePaymentGateway.java`
- `backend/src/main/java/br/com/ricarte/assinaflow/payment/stripe/StripeWebhooks.java`
- `backend/src/main/resources/db/changelog/changes/007-payment-ledger.yaml`
- `backend/src/main/resources/db/changelog/changes/008-stripe-sandbox.yaml`
- `backend/src/test/java/br/com/ricarte/assinaflow/PaymentRecoveryIntegrationTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/SecurityFlowIntegrationTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/auth/BillingModeGuardTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/auth/JwtServiceTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/payment/HttpPaymentGatewayTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/payment/PaymentRecoverySchedulingTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/payment/stripe/StripeContractIntegrationTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/subscription/PaymentChargeConsumerTest.java`
- `backend/src/test/java/br/com/ricarte/assinaflow/testutil/PatchedDockerClientProviderStrategy.java`
- `backend/src/test/resources/META-INF/services/org.testcontainers.dockerclient.DockerClientProviderStrategy`
- `backend/src/test/resources/db.changelog-pre-ledger.yaml`
- `deploy/images/postgres.Dockerfile`
- `deploy/images/rabbitmq.Dockerfile`
- `deploy/images/redis.Dockerfile`
- `docs/validation/closure.md`
- `docs/validation/files.md`
- `docs/validation/operations.md`
- `docs/validation/security-results.json`
- `frontend/.dockerignore`
- `frontend/patch-npm-undici.cjs`
- `frontend/src/StripeBillingPanel.tsx`
- `scripts/verify-isolated.py`
- `scripts/verify-stack.py`
- `scripts/verify-stripe-sandbox.py`

## Comparação e arquivos essenciais

Esta execução acrescentou 4 caminhos rastreados modificados e 15 arquivos novos ao conjunto recebido, sem remoções. As mudanças concentram Stripe, migração mínima do framework/segurança, manutenção de ferramentas e verificadores; não foram adicionados microsserviços ou recursos comerciais opcionais.

Conferidos e não ignorados: migrations001–008 e master, application.yml/docker, classes Stripe e APIs de titular, ledger/settlement/recovery, frontend de cadastro/autenticação, workflow, Dockerfiles de runtime/build/manutenção, patch npm com integridade, testes de contrato/recuperação e fixture antiga. **O arquivo ServiceLoader em backend/src/test/resources/META-INF/services e a estratégia em testutil são necessários ao transporte Docker de teste corrigido**; incluir ambos na revisão/versionamento. scripts/verify-stack.py e scripts/verify-stripe-sandbox.py são verificadores operacionais, não relatórios duplicados.

Os três documentos existentes foram atualizados no lugar; security-results.json conserva grafo SBOM, achados/versões/fontes, IDs/plataformas, bases e hashes. Dados de cartão, credenciais reais e este pedido não foram salvos. LICENSE e atribuições legais existentes foram preservados.

`git diff --check` aprovado. Validação local não equivale a clone limpo de futuro commit: arquivos novos permanecem fora do índice por instrução do proprietário. Versionar o conjunto completo antes de acionar a CI remota.
