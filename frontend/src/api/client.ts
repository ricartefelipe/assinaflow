import {
  ApiError,
  type AuthResponse,
  type OutboxEvent,
  type PaymentBehavior,
  type Plan,
  type PlanInfo,
  type ProblemDetail,
  type Subscription,
  type User,
} from '../types'

const TOKEN_KEY = 'assinaflow.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

async function request<T>(path: string, init: RequestInit = {}, auth = false): Promise<T | null> {
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json')
  }
  if (auth) {
    const token = getToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
  }

  const response = await fetch(path, { ...init, headers })

  if (response.status === 204) {
    return null
  }

  const text = await response.text()
  const body = text ? JSON.parse(text) : null

  if (!response.ok) {
    throw new ApiError(response.status, (body || {}) as ProblemDetail)
  }

  return body as T
}

export const api = {
  listPlans(): Promise<PlanInfo[]> {
    return request<PlanInfo[]>('/api/v1/plans') as Promise<PlanInfo[]>
  },

  register(payload: { nome: string; email: string; senha: string }): Promise<AuthResponse> {
    return request<AuthResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    }) as Promise<AuthResponse>
  },

  login(payload: { email: string; senha: string }): Promise<AuthResponse> {
    return request<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    }) as Promise<AuthResponse>
  },

  me(): Promise<User> {
    return request<User>('/api/v1/auth/me', {}, true) as Promise<User>
  },

  getActive(userId: string): Promise<Subscription | null> {
    return request<Subscription>(`/api/v1/users/${userId}/subscriptions/active`, {}, true)
  },

  createSubscription(userId: string, plano: Plan): Promise<Subscription> {
    return request<Subscription>(`/api/v1/users/${userId}/subscriptions`, {
      method: 'POST',
      body: JSON.stringify({ plano }),
    }, true) as Promise<Subscription>
  },

  cancelSubscription(userId: string): Promise<Subscription> {
    return request<Subscription>(`/api/v1/users/${userId}/subscriptions/cancel`, {
      method: 'POST',
    }, true) as Promise<Subscription>
  },

  resumeSubscription(userId: string): Promise<Subscription> {
    return request<Subscription>(`/api/v1/users/${userId}/subscriptions/resume`, {
      method: 'POST',
    }, true) as Promise<Subscription>
  },

  reactivateSubscription(userId: string): Promise<Subscription> {
    return request<Subscription>(`/api/v1/users/${userId}/subscriptions/reactivate`, {
      method: 'POST',
    }, true) as Promise<Subscription>
  },

  changePlan(userId: string, plano: Plan): Promise<Subscription> {
    return request<Subscription>(`/api/v1/users/${userId}/subscriptions/change-plan`, {
      method: 'POST',
      body: JSON.stringify({ plano }),
    }, true) as Promise<Subscription>
  },

  adminListUsers(limit = 100): Promise<User[]> {
    return request<User[]>(`/api/v1/admin/users?limit=${limit}`, {}, true) as Promise<User[]>
  },

  adminListSubscriptions(limit = 100): Promise<Subscription[]> {
    return request<Subscription[]>(`/api/v1/admin/subscriptions?limit=${limit}`, {}, true) as Promise<Subscription[]>
  },

  adminListOutbox(status = 'DEAD', limit = 50): Promise<OutboxEvent[]> {
    return request<OutboxEvent[]>(`/api/v1/admin/outbox?status=${status}&limit=${limit}`, {}, true) as Promise<OutboxEvent[]>
  },

  adminRequeueOutbox(id: string): Promise<OutboxEvent> {
    return request<OutboxEvent>(`/api/v1/admin/outbox/${id}/requeue`, { method: 'POST' }, true) as Promise<OutboxEvent>
  },

  adminUpdatePaymentProfile(userId: string, behavior: PaymentBehavior, failNextN: number): Promise<User> {
    return request<User>(`/api/v1/admin/users/${userId}/payment-profile`, {
      method: 'PUT',
      body: JSON.stringify({ behavior, failNextN }),
    }, true) as Promise<User>
  },
}
