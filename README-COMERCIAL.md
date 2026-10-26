# AssinaFlow — kit de demonstração de assinaturas

**Status: candidato a piloto sintético; cobrança real e oferta como motor financeiro pronto para produção não estão liberadas.** Veja [evidências e bloqueios](docs/validation/closure.md) e [manual operacional](docs/validation/operations.md).

| Item | Oferta delimitada |
|---|---|
| ICP / comprador | Equipes Java/Spring que precisam avaliar um ponto de partida para assinaturas; comprador técnico responsável pela integração |
| Usuário | Desenvolvedor, operador e assinante de demonstração |
| Dor a verificar no piloto | Tempo gasto em gestão de ciclos, cancelamento, concorrência e entrega de eventos |
| Incluído | Código Java 21/Spring, PostgreSQL/Liquibase, portal Vite, login próprio, planos fixos em BRL, pagamento simulado, renovação/retry/suspensão, cancelamento, outbox, obrigações/execuções persistentes, consulta/reconciliação com simulador e Stripe sandbox, cadastro hospedado, webhook autenticado e console ADMIN |
| Excluído | Homologação externa de gateway, dinheiro real, armazenamento de cartão, invoice fiscal, refund/chargeback, trial, cobrança por uso, SSO, multi-tenancy organizacional, serviço gerenciado e garantia exactly-once |
| Modalidade | Licença instalada conforme LICENSE; implantação assistida e suporte dependem de contrato separado |
| Custos | Infraestrutura, operação, backup, segurança, e-mail e provedor de pagamento a cargo do contratante conforme acordo; sem preço fixado nesta documentação |
| Limites | Uma assinatura ativa ou com cancelamento agendado por usuário; sem plano versionado ou auditoria completa de admin; reconciliação durável com simulador e contrato HTTP Stripe local; sandbox externo pendente |
| Dependências | Java, PostgreSQL; Redis/RabbitMQ no perfil docker; Stripe definido; conta sandbox e homologação externa pendentes |
| Suporte / atualização | Canal, horário, SLA, prazo, versões e responsabilidades a definir pelo proprietário antes da proposta |

Onboarding: acordar escopo e licença, preparar ambiente isolado, criar dados sintéticos, executar gates, demonstrar cadastro/login → plano → renovação simulada → cancelamento → restore. Não usar clientes ou cartões reais. O piloto deve registrar falhas, tempos e requisitos que ainda não estão atendidos; não há validação de demanda ou cliente comprovada nesta entrega.

Offboarding: exportação e retenção precisam ser contratadas e implementadas conforme necessidade do comprador; revogar acesso, entregar backup autorizado e comprovar restauração antes de encerrar operação. Não prometer portabilidade integral já disponível.

Objeções: não é um serviço de billing gerenciado nem uma instituição de pagamento. Constraints e locks protegem o banco local, mas não resolvem sozinhos sucesso remoto seguido de crash. O HTTP genérico não implementa consulta e é bloqueado no serviço financeiro; o simulador e o adapter Stripe cumprem o contrato de recuperação, com testes locais e homologação externa pendente.

Condição de venda: o proprietário decide direitos/licença, preço, suporte e contratos. Uma licença de avaliação ou implantação delimitada não autoriza anunciar cobrança real confiável. A oferta financeira permanece NO-GO até executar a homologação Stripe sandbox e resolver as decisões de segurança e operação registradas no relatório. Persistência, recuperação, adapter Stripe e webhook estão implementados e testados localmente. Depois, executar piloto autorizado e CI sobre o commit publicado pelo proprietário. Não há aprovação jurídica, certificação, receita ou cliente declarado aqui.

Termos existentes: [LICENSE](LICENSE). Esta página não amplia nem substitui direitos contratuais.
