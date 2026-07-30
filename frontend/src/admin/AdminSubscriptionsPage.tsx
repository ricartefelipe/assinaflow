import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/client'
import {
  ApiError,
  formatPrice,
  planLabel,
  statusLabel,
  type Subscription,
  type SubscriptionStatus,
} from '../types'

const FILTERS: Array<{ value: 'ALL' | SubscriptionStatus; label: string }> = [
  { value: 'ALL', label: 'Todas' },
  { value: 'ATIVA', label: 'Ativas' },
  { value: 'CANCELAMENTO_AGENDADO', label: 'Cancel. agendado' },
  { value: 'SUSPENSA', label: 'Suspensas' },
  { value: 'CANCELADA', label: 'Canceladas' },
]

export function AdminSubscriptionsPage() {
  const [subs, setSubs] = useState<Subscription[]>([])
  const [filter, setFilter] = useState<'ALL' | SubscriptionStatus>('ALL')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.adminListSubscriptions()
      .then(setSubs)
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : 'Falha ao carregar assinaturas.')
      })
      .finally(() => setLoading(false))
  }, [])

  const filtered = useMemo(
    () => (filter === 'ALL' ? subs : subs.filter((s) => s.status === filter)),
    [subs, filter],
  )

  if (loading) {
    return <p className="muted">Carregando assinaturas…</p>
  }

  return (
    <div className="admin-page">
      <header className="admin-page-head">
        <div>
          <p className="eyebrow">Billing</p>
          <h1>Assinaturas</h1>
          <p className="muted">Ciclos, renovação automática e créditos — sem faturas (API).</p>
        </div>
      </header>

      {error && <p className="error">{error}</p>}

      <div className="filter-row" role="tablist" aria-label="Filtrar status">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            role="tab"
            aria-selected={filter === f.value}
            className={`filter-chip${filter === f.value ? ' active' : ''}`}
            onClick={() => setFilter(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>

      <section className="admin-panel">
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Plano</th>
                <th>Status</th>
                <th>Início</th>
                <th>Expiração</th>
                <th>Auto renew</th>
                <th>Falhas</th>
                <th>Crédito</th>
                <th>Próx. tentativa</th>
                <th>Usuário</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((s) => (
                <tr key={s.id}>
                  <td>{planLabel(s.plano)}</td>
                  <td><span className={`pill status-${s.status}`}>{statusLabel(s.status)}</span></td>
                  <td className="mono">{s.dataInicio}</td>
                  <td className="mono">{s.dataExpiracao}</td>
                  <td>{s.autoRenew ? 'Sim' : 'Não'}</td>
                  <td>{s.renewalFailures}</td>
                  <td>{formatPrice(s.creditoRenovacaoCentavos ?? 0)}</td>
                  <td className="mono tiny">
                    {s.nextRenewalAttemptAt
                      ? new Date(s.nextRenewalAttemptAt).toLocaleString('pt-BR')
                      : '—'}
                  </td>
                  <td className="mono tiny" title={s.usuarioId}>{s.usuarioId.slice(0, 8)}…</td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr><td colSpan={9} className="muted">Nenhuma assinatura neste filtro.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
