import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { ApiError, type OutboxEvent, type OutboxStatus } from '../types'

const STATUSES: OutboxStatus[] = ['DEAD', 'PENDING', 'SENT']

export function AdminOperationsPage() {
  const [status, setStatus] = useState<OutboxStatus>('DEAD')
  const [events, setEvents] = useState<OutboxEvent[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  async function refresh(next = status) {
    const list = await api.adminListOutbox(next)
    setEvents(list)
  }

  useEffect(() => {
    setLoading(true)
    refresh(status)
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : 'Falha ao carregar outbox.')
        setEvents([])
      })
      .finally(() => setLoading(false))
  }, [status])

  async function requeue(id: string) {
    setBusyId(id)
    setError(null)
    try {
      await api.adminRequeueOutbox(id)
      await refresh()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao reenfileirar.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="admin-page">
      <header className="admin-page-head">
        <div>
          <p className="eyebrow">Confiabilidade</p>
          <h1>Operações</h1>
          <p className="muted">Outbox de cobrança assíncrona — requeue de eventos DEAD/PENDING.</p>
        </div>
      </header>

      {error && <p className="error">{error}</p>}

      <div className="filter-row" role="tablist" aria-label="Status do outbox">
        {STATUSES.map((s) => (
          <button
            key={s}
            type="button"
            role="tab"
            aria-selected={status === s}
            className={`filter-chip${status === s ? ' active' : ''}`}
            onClick={() => setStatus(s)}
          >
            {s}
          </button>
        ))}
      </div>

      <section className="admin-panel">
        {loading ? (
          <p className="muted">Carregando outbox…</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Tipo</th>
                  <th>Status</th>
                  <th>Tentativas</th>
                  <th>Erro</th>
                  <th>Ação</th>
                </tr>
              </thead>
              <tbody>
                {events.map((e) => {
                  const canRequeue = e.status === 'DEAD' || e.status === 'PENDING'
                  return (
                    <tr key={e.id}>
                      <td className="mono">{e.eventType}</td>
                      <td><span className="pill">{e.status}</span></td>
                      <td>{e.attempts ?? '—'}</td>
                      <td className="tiny">{e.lastError || '—'}</td>
                      <td>
                        {canRequeue && (
                          <button
                            className="btn btn-primary btn-sm"
                            type="button"
                            disabled={busyId === e.id}
                            onClick={() => requeue(e.id)}
                          >
                            {busyId === e.id ? 'Reenqueue…' : 'Requeue'}
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
                {events.length === 0 && (
                  <tr><td colSpan={5} className="muted">Nenhum evento {status}.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
