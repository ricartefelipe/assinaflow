import { useEffect, useState } from 'react'
import { api } from '../api/client'
import {
  ApiError,
  paymentBehaviorLabel,
  type PaymentBehavior,
  type User,
} from '../types'

export function AdminCustomersPage() {
  const [users, setUsers] = useState<User[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  async function refresh() {
    const list = await api.adminListUsers()
    setUsers(list)
  }

  useEffect(() => {
    refresh()
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : 'Falha ao carregar clientes.')
      })
      .finally(() => setLoading(false))
  }, [])

  async function updateProfile(userId: string, behavior: PaymentBehavior, failNextN: number) {
    setBusyId(userId)
    setError(null)
    try {
      await api.adminUpdatePaymentProfile(userId, behavior, failNextN)
      await refresh()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao atualizar perfil de pagamento.')
    } finally {
      setBusyId(null)
    }
  }

  if (loading) {
    return <p className="muted">Carregando clientes…</p>
  }

  return (
    <div className="admin-page">
      <header className="admin-page-head">
        <div>
          <p className="eyebrow">Base</p>
          <h1>Clientes</h1>
          <p className="muted">Usuários e simulação de cobrança (payment profile).</p>
        </div>
      </header>

      {error && <p className="error">{error}</p>}

      <section className="admin-panel">
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Nome</th>
                <th>E-mail</th>
                <th>Papel</th>
                <th>Pagamento</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const behavior = u.paymentBehavior ?? 'ALWAYS_APPROVE'
                const busy = busyId === u.id
                return (
                  <tr key={u.id}>
                    <td>{u.nome}</td>
                    <td>{u.email}</td>
                    <td><span className="pill">{u.role ?? 'USER'}</span></td>
                    <td>
                      {paymentBehaviorLabel(behavior)}
                      {behavior === 'FAIL_NEXT_N' && u.paymentFailNextN != null
                        ? ` (${u.paymentFailNextN})`
                        : ''}
                    </td>
                    <td>
                      <div className="row-actions">
                        <button
                          className="btn btn-ghost btn-sm"
                          type="button"
                          disabled={busy || behavior === 'ALWAYS_APPROVE'}
                          onClick={() => updateProfile(u.id, 'ALWAYS_APPROVE', 0)}
                        >
                          Aprovar
                        </button>
                        <button
                          className="btn btn-ghost btn-sm"
                          type="button"
                          disabled={busy || behavior === 'ALWAYS_DECLINE'}
                          onClick={() => updateProfile(u.id, 'ALWAYS_DECLINE', 0)}
                        >
                          Recusar
                        </button>
                        <button
                          className="btn btn-ghost btn-sm"
                          type="button"
                          disabled={busy}
                          onClick={() => updateProfile(u.id, 'FAIL_NEXT_N', 2)}
                        >
                          Falhar 2×
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
              {users.length === 0 && (
                <tr><td colSpan={5} className="muted">Nenhum cliente.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
