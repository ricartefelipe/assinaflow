export type Plan = 'BASICO' | 'PREMIUM' | 'FAMILIA'

export type SubscriptionStatus =
  | 'ATIVA'
  | 'CANCELAMENTO_AGENDADO'
  | 'CANCELADA'
  | 'SUSPENSA'

export interface User {
  id: string
  email: string
  nome: string
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
  nextRenewalAttemptAt: string | null
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
