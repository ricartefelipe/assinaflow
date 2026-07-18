import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { api } from './api/client'
import { useAuth } from './auth/AuthContext'
import { ApiError, formatPrice, planLabel, type Plan, type PlanInfo, type Subscription } from './types'

export function HomePage() {
  const { user } = useAuth()
  const [plans, setPlans] = useState<PlanInfo[]>([])

  useEffect(() => {
    api.listPlans().then(setPlans).catch(() => setPlans([]))
  }, [])

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">AssinaFlow</div>
        <div className="nav-actions">
          {user ? (
            <Link className="btn btn-primary" to="/conta">Minha conta</Link>
          ) : (
            <>
              <Link className="btn btn-ghost" to="/entrar">Entrar</Link>
              <Link className="btn btn-primary" to="/cadastro">Assinar</Link>
            </>
          )}
        </div>
      </header>

      <section className="hero">
        <h1>AssinaFlow</h1>
        <p>Escolha um plano, assista no seu ritmo e cancele quando quiser — sem perder o ciclo já pago.</p>
        <div className="plans">
          {plans.map((plan) => (
            <article key={plan.plano} className={`plan ${plan.plano === 'PREMIUM' ? 'featured' : ''}`}>
              <h3>{planLabel(plan.plano)}</h3>
              <p className="price">{formatPrice(plan.precoCentavos, plan.moeda)}</p>
              <p className="muted">Cobrança mensal simulada para este ambiente.</p>
              <Link className="btn btn-primary" to={user ? `/contratar?plano=${plan.plano}` : '/cadastro'}>
                Começar com {planLabel(plan.plano)}
              </Link>
            </article>
          ))}
        </div>
      </section>
    </div>
  )
}

export function CadastroPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) {
    return <Navigate to="/contratar" replace />
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await register(nome, email, senha)
      navigate('/contratar')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Não foi possível criar a conta.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Criar conta" subtitle="Cadastre-se para escolher um plano.">
      <form className="form" onSubmit={onSubmit}>
        <label>
          Nome
          <input value={nome} onChange={(e) => setNome(e.target.value)} required maxLength={120} />
        </label>
        <label>
          E-mail
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Senha
          <input type="password" value={senha} onChange={(e) => setSenha(e.target.value)} required minLength={8} />
        </label>
        {error && <p className="error">{error}</p>}
        <button className="btn btn-primary" disabled={submitting} type="submit">
          {submitting ? 'Criando…' : 'Criar conta'}
        </button>
        <p className="muted">Já tem conta? <Link to="/entrar">Entrar</Link></p>
      </form>
    </AuthShell>
  )
}

export function EntrarPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) {
    return <Navigate to="/conta" replace />
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(email, senha)
      navigate('/conta')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Não foi possível entrar.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Entrar" subtitle="Acesse sua conta AssinaFlow.">
      <form className="form" onSubmit={onSubmit}>
        <label>
          E-mail
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Senha
          <input type="password" value={senha} onChange={(e) => setSenha(e.target.value)} required minLength={8} />
        </label>
        {error && <p className="error">{error}</p>}
        <button className="btn btn-primary" disabled={submitting} type="submit">
          {submitting ? 'Entrando…' : 'Entrar'}
        </button>
        <p className="muted">Novo por aqui? <Link to="/cadastro">Criar conta</Link></p>
      </form>
    </AuthShell>
  )
}

export function ContaPage() {
  const { user, logout, loading } = useAuth()
  const [subscription, setSubscription] = useState<Subscription | null | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!user) return
    api.getActive(user.id)
      .then(setSubscription)
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : 'Falha ao carregar assinatura.')
        setSubscription(null)
      })
  }, [user])

  if (loading) {
    return <div className="shell page"><p className="muted">Carregando…</p></div>
  }

  if (!user) {
    return <Navigate to="/entrar" replace />
  }

  async function cancel() {
    if (!user || !subscription) return
    if (!window.confirm('Cancelar a renovação? Você mantém o acesso até o fim do ciclo atual.')) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      const updated = await api.cancelSubscription(user.id)
      setSubscription(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Não foi possível cancelar.')
    } finally {
      setBusy(false)
    }
  }

  async function resume() {
    if (!user) return
    setBusy(true)
    setError(null)
    try {
      const updated = await api.resumeSubscription(user.id)
      setSubscription(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Não foi possível retomar.')
    } finally {
      setBusy(false)
    }
  }

  async function reactivate() {
    if (!user) return
    setBusy(true)
    setError(null)
    try {
      const updated = await api.reactivateSubscription(user.id)
      setSubscription(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Não foi possível reativar.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="shell page stack">
      <header className="topbar">
        <Link className="brand" to="/">AssinaFlow</Link>
        <div className="nav-actions">
          <span className="muted">{user.nome}</span>
          <button className="btn btn-ghost" type="button" onClick={logout}>Sair</button>
        </div>
      </header>

      <section className="panel stack">
        <h2>Minha assinatura</h2>
        {subscription === undefined && <p className="muted">Carregando status…</p>}
        {subscription === null && (
          <>
            <p>Você ainda não tem uma assinatura ativa.</p>
            <Link className="btn btn-primary" to="/contratar">Escolher um plano</Link>
          </>
        )}
        {subscription && (
          <>
            <div className="status-pill">{statusLabel(subscription.status)}</div>
            <h3>{planLabel(subscription.plano)}</h3>
            <p className="muted">
              Ciclo de {subscription.dataInicio} até {subscription.dataExpiracao}.
              {subscription.status === 'CANCELAMENTO_AGENDADO'
                ? ' O acesso continua até a data de expiração.'
                : subscription.autoRenew
                  ? ' Renovação automática ligada.'
                  : ''}
            </p>
            {subscription.status === 'SUSPENSA' && (
              <>
                <p className="error">A renovação falhou após várias tentativas. Você pode reativar para abrir um novo ciclo.</p>
                <button className="btn btn-primary" type="button" disabled={busy} onClick={reactivate}>
                  {busy ? 'Reativando…' : 'Reativar assinatura'}
                </button>
              </>
            )}
            {subscription.status === 'ATIVA' && (
              <div className="stack" style={{ gap: '0.75rem' }}>
                <Link className="btn btn-ghost" to="/trocar-plano">Trocar plano</Link>
                <button className="btn btn-danger" type="button" disabled={busy} onClick={cancel}>
                  {busy ? 'Cancelando…' : 'Cancelar assinatura'}
                </button>
              </div>
            )}
            {subscription.status === 'CANCELAMENTO_AGENDADO' && (
              <button className="btn btn-primary" type="button" disabled={busy} onClick={resume}>
                {busy ? 'Retomando…' : 'Manter assinatura'}
              </button>
            )}
          </>
        )}
        {error && <p className="error">{error}</p>}
      </section>
    </div>
  )
}

export function ContratarPage() {
  const { user, loading } = useAuth()
  const navigate = useNavigate()
  const [plans, setPlans] = useState<PlanInfo[]>([])
  const [selected, setSelected] = useState<Plan>('PREMIUM')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const plano = params.get('plano') as Plan | null
    if (plano === 'BASICO' || plano === 'PREMIUM' || plano === 'FAMILIA') {
      setSelected(plano)
    }
    api.listPlans().then(setPlans).catch(() => setPlans([]))
  }, [])

  if (loading) {
    return <div className="shell page"><p className="muted">Carregando…</p></div>
  }

  if (!user) {
    return <Navigate to="/cadastro" replace />
  }

  async function confirm() {
    setBusy(true)
    setError(null)
    try {
      await api.createSubscription(user!.id, selected)
      navigate('/conta')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Não foi possível contratar.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="shell page stack">
      <header className="topbar">
        <Link className="brand" to="/">AssinaFlow</Link>
        <Link className="btn btn-ghost" to="/conta">Minha conta</Link>
      </header>
      <section className="panel stack">
        <h2>Contratar plano</h2>
        <p className="muted">Confirme o plano para ativar sua assinatura.</p>
        <div className="plans">
          {plans.map((plan) => (
            <button
              key={plan.plano}
              type="button"
              className={`plan ${selected === plan.plano ? 'featured' : ''}`}
              onClick={() => setSelected(plan.plano)}
              style={{ textAlign: 'left', cursor: 'pointer', width: '100%' }}
            >
              <h3>{planLabel(plan.plano)}</h3>
              <p className="price">{formatPrice(plan.precoCentavos, plan.moeda)}</p>
            </button>
          ))}
        </div>
        {error && <p className="error">{error}</p>}
        <button className="btn btn-primary" type="button" disabled={busy} onClick={confirm}>
          {busy ? 'Ativando…' : `Ativar ${planLabel(selected)}`}
        </button>
      </section>
    </div>
  )
}

export function TrocarPlanoPage() {
  const { user, loading } = useAuth()
  const navigate = useNavigate()
  const [plans, setPlans] = useState<PlanInfo[]>([])
  const [current, setCurrent] = useState<Subscription | null>(null)
  const [selected, setSelected] = useState<Plan | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    if (!user) return
    Promise.all([api.listPlans(), api.getActive(user.id)])
      .then(([planList, sub]) => {
        setPlans(planList)
        setCurrent(sub)
        if (sub && sub.status === 'ATIVA') {
          const next = planList.find((p) => p.plano !== sub.plano)?.plano ?? null
          setSelected(next)
        }
      })
      .catch(() => {
        setPlans([])
        setCurrent(null)
      })
      .finally(() => setReady(true))
  }, [user])

  if (loading || !ready) {
    return <div className="shell page"><p className="muted">Carregando…</p></div>
  }

  if (!user) {
    return <Navigate to="/entrar" replace />
  }

  if (!current || current.status !== 'ATIVA') {
    return <Navigate to="/conta" replace />
  }

  async function confirm() {
    if (!selected) return
    setBusy(true)
    setError(null)
    try {
      await api.changePlan(user!.id, selected)
      navigate('/conta')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Não foi possível trocar o plano.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="shell page stack">
      <header className="topbar">
        <Link className="brand" to="/">AssinaFlow</Link>
        <Link className="btn btn-ghost" to="/conta">Minha conta</Link>
      </header>
      <section className="panel stack">
        <h2>Trocar plano</h2>
        <p className="muted">
          Plano atual: {planLabel(current.plano)}. A troca vale imediatamente e mantém o ciclo até{' '}
          {current.dataExpiracao}.
        </p>
        <div className="plans">
          {plans.map((plan) => {
            const isCurrent = plan.plano === current.plano
            return (
              <button
                key={plan.plano}
                type="button"
                className={`plan ${selected === plan.plano ? 'featured' : ''}`}
                onClick={() => !isCurrent && setSelected(plan.plano)}
                disabled={isCurrent}
                style={{ textAlign: 'left', cursor: isCurrent ? 'default' : 'pointer', width: '100%', opacity: isCurrent ? 0.55 : 1 }}
              >
                <h3>{planLabel(plan.plano)}{isCurrent ? ' (atual)' : ''}</h3>
                <p className="price">{formatPrice(plan.precoCentavos, plan.moeda)}</p>
              </button>
            )
          })}
        </div>
        {error && <p className="error">{error}</p>}
        <button
          className="btn btn-primary"
          type="button"
          disabled={busy || !selected || selected === current.plano}
          onClick={confirm}
        >
          {busy ? 'Salvando…' : selected ? `Mudar para ${planLabel(selected)}` : 'Escolha um plano'}
        </button>
      </section>
    </div>
  )
}

function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="shell page">
      <header className="topbar">
        <Link className="brand" to="/">AssinaFlow</Link>
      </header>
      <section className="panel stack">
        <h2>{title}</h2>
        <p className="muted">{subtitle}</p>
        {children}
      </section>
    </div>
  )
}

function statusLabel(status: Subscription['status']): string {
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
