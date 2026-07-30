export type Plan = 'BASICO' | 'PREMIUM' | 'FAMILIA'

export type SubscriptionStatus =
  | 'ATIVA'
  | 'CANCELAMENTO_AGENDADO'
  | 'CANCELADA'
  | 'SUSPENSA'

export type PaymentBehavior = 'ALWAYS_APPROVE' | 'ALWAYS_DECLINE' | 'FAIL_NEXT_N'

export type OutboxStatus = 'PENDING' | 'SENT' | 'DEAD'

export interface User {
  id: string
  email: string
  nome: string
  role?: 'USER' | 'ADMIN'
  paymentBehavior?: PaymentBehavior
  paymentFailNextN?: number
}

export interface PlanInfo {
  plano: Plan
  precoCentavos: number
  moeda: string
}

export interface Subscription {
  id: string
  usuarioId: string
  plano: Plan
  dataInicio: string
  dataExpiracao: string
  status: SubscriptionStatus
  autoRenew: boolean
  renewalFailures: number
  creditoRenovacaoCentavos?: number
  nextRenewalAttemptAt: string | null
}

export interface OutboxEvent {
  id: string
  eventType: string
  status: string
  attempts?: number
  lastError?: string
  createdAt?: string
  updatedAt?: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  user: User
}

export interface ProblemDetail {
  title?: string
  detail?: string
  code?: string
  status?: number
  requestId?: string
  violations?: Record<string, string>
}

export class ApiError extends Error {
  status: number
  problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail || problem.title || 'Erro inesperado')
    this.status = status
    this.problem = problem
  }
}

export function formatPrice(cents: number, currency = 'BRL'): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency,
  }).format(cents / 100)
}

export function planLabel(plan: Plan): string {
  switch (plan) {
    case 'BASICO':
      return 'Básico'
    case 'PREMIUM':
      return 'Premium'
    case 'FAMILIA':
      return 'Família'
    default: {
      const _exhaustive: never = plan
      return _exhaustive
    }
  }
}

export function statusLabel(status: SubscriptionStatus): string {
  switch (status) {
    case 'ATIVA':
      return 'Ativa'
    case 'CANCELAMENTO_AGENDADO':
      return 'Cancelamento agendado'
    case 'CANCELADA':
      return 'Cancelada'
    case 'SUSPENSA':
      return 'Suspensa'
    default: {
      const _exhaustive: never = status
      return _exhaustive
    }
  }
}

export function paymentBehaviorLabel(behavior: PaymentBehavior): string {
  switch (behavior) {
    case 'ALWAYS_APPROVE':
      return 'Sempre aprova'
    case 'ALWAYS_DECLINE':
      return 'Sempre recusa'
    case 'FAIL_NEXT_N':
      return 'Falha N vezes'
    default: {
      const _exhaustive: never = behavior
      return _exhaustive
    }
  }
}
