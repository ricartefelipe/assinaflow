import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import {
  ApiError,
  formatPrice,
  planLabel,
  statusLabel,
  type Subscription,
  type User,
} from '../types'

function countByStatus(subs: Subscription[], status: Subscription['status']): number {
  return subs.filter((s) => s.status === status).length
}

export function AdminDashboardPage() {
  const [users, setUsers] = useState<User[]>([])
  const [subs, setSubs] = useState<Subscription[]>([])
  const [deadCount, setDeadCount] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      api.adminListUsers(),
      api.adminListSubscriptions(),
      api.adminListOutbox('DEAD', 50),
    ])
      .then(([u, s, o]) => {
        setUsers(u)
        setSubs(s)
        setDeadCount(o.length)
      })
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : 'Falha ao carregar dashboard.')
      })
      .finally(() => setLoading(false))
  }, [])

  const creditTotal = useMemo(
    () => subs.reduce((acc, s) => acc + (s.creditoRenovacaoCentavos ?? 0), 0),
    [subs],
  )

  const recent = useMemo(() => subs.slice(0, 8), [subs])

  if (loading) {
    return <p className="muted">Carregando métricas…</p>
  }

  return (
    <div className="admin-page">
      <header className="admin-page-head">
        <div>
          <p className="eyebrow">Operação</p>
          <h1>Dashboard</h1>
          <p className="muted">Assinaturas, cobrança simulada e saúde do outbox.</p>
        </div>
      </header>

      {error && <p className="error">{error}</p>}

      <section className="kpi-grid">
        <article className="kpi">
          <p className="kpi-label">Clientes</p>
          <p className="kpi-value">{users.length}</p>
        </article>
        <article className="kpi">
          <p className="kpi-label">Assinaturas ativas</p>
          <p className="kpi-value">{countByStatus(subs, 'ATIVA')}</p>
        </article>
        <article className="kpi">
          <p className="kpi-label">Cancelamento agendado</p>
          <p className="kpi-value">{countByStatus(subs, 'CANCELAMENTO_AGENDADO')}</p>
        </article>
        <article className="kpi warn">
          <p className="kpi-label">Suspensas</p>
          <p className="kpi-value">{countByStatus(subs, 'SUSPENSA')}</p>
        </article>
        <article className="kpi money">
          <p className="kpi-label">Crédito de renovação</p>
          <p className="kpi-value">{formatPrice(creditTotal)}</p>
        </article>
        <article className={`kpi${deadCount > 0 ? ' danger' : ''}`}>
          <p className="kpi-label">Outbox DEAD</p>
          <p className="kpi-value">{deadCount}</p>
          {deadCount > 0 && (
            <Link className="kpi-link" to="/admin/operacoes">Ver operações</Link>
          )}
        </article>
      </section>

      <section className="admin-panel">
        <div className="admin-panel-head">
          <h2>Assinaturas recentes</h2>
          <Link className="btn btn-ghost btn-sm" to="/admin/assinaturas">Ver todas</Link>
        </div>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Plano</th>
                <th>Status</th>
                <th>Ciclo</th>
                <th>Renovação</th>
                <th>Usuário</th>
              </tr>
            </thead>
            <tbody>
              {recent.map((s) => (
                <tr key={s.id}>
                  <td>{planLabel(s.plano)}</td>
                  <td><span className={`pill status-${s.status}`}>{statusLabel(s.status)}</span></td>
                  <td className="mono">{s.dataInicio} → {s.dataExpiracao}</td>
                  <td>{s.autoRenew ? 'Auto' : 'Off'}{s.renewalFailures > 0 ? ` · ${s.renewalFailures} falha(s)` : ''}</td>
                  <td className="mono tiny">{s.usuarioId.slice(0, 8)}…</td>
                </tr>
              ))}
              {recent.length === 0 && (
                <tr><td colSpan={5} className="muted">Nenhuma assinatura.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
