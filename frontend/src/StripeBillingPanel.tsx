import { useEffect, useState } from 'react'
import { api } from './api/client'
import { hostedCheckoutUrl } from './billing/hostedCheckout'

type StripeClient = {confirmCardPayment: (secret: string, options: {payment_method: string}) => Promise<{error?: {message?: string}}>}
async function stripeClient(key: string): Promise<StripeClient> {
  const host = window as unknown as {Stripe?: (key: string) => StripeClient}
  if (!host.Stripe) await new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://js.stripe.com/v3/'
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Não foi possível carregar o provedor.'))
    document.head.appendChild(script)
  })
  if (!host.Stripe) throw new Error('Provedor indisponível.')
  return host.Stripe(key)
}
export function StripeBillingPanel({userId}: {userId: string}) {
  const [key, setKey] = useState('')
  const [ready, setReady] = useState(false)
  const [payments, setPayments] = useState<{id: string; status: string; settled: boolean}[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    api.billingConfig().then(async config => {
      if (config?.gateway !== 'stripe') return
      setKey(config.publishableKey || '')
      setReady((await api.billingStatus(userId))?.ready || false)
      setPayments((await api.payments(userId)) || [])
    }).catch(() => setError('Não foi possível consultar o pagamento.'))
  }, [userId])
  if (!key) return null
  async function setup() {
    setBusy(true); setError('')
    try {
      const result = await api.billingSetup(userId)
      if (!result) throw new Error('Cadastro indisponível.')
      window.location.assign(hostedCheckoutUrl(result.url))
    } catch {setError('Não foi possível abrir o cadastro seguro.'); setBusy(false)}
  }
  async function action(id: string) {
    setBusy(true); setError('')
    try {
      const result = await api.stripeAction(userId, id)
      if (result?.clientSecret && result.paymentMethod) {
        const stripe = await stripeClient(key)
        const response = await stripe.confirmCardPayment(result.clientSecret, {payment_method: result.paymentMethod})
        if (response.error) throw new Error('Autenticação não concluída. Tente novamente no provedor.')
      }
      await api.stripeReconcile(userId, id)
      window.location.assign('/conta')
    } catch (e) {setError(e instanceof Error ? e.message : 'Pagamento ainda em processamento.'); setBusy(false)}
  }
  return <div className="stack">
    <p>Stripe sandbox: somente meios de pagamento de teste. {ready ? 'Meio de pagamento cadastrado.' : 'Cadastre o cartão de teste na página segura antes de contratar.'}</p>
    <button className="btn btn-ghost" disabled={busy} onClick={setup}>Cadastrar meio de pagamento de teste</button>
    {payments.filter(p => !p.settled && p.status !== 'CANCELED').map(p => <div key={p.id}>
      <p>Pagamento: {p.status}. O acesso depende da confirmação pelo servidor.</p>
      <button className="btn btn-primary" disabled={busy} onClick={() => action(p.id)}>Verificar ou autenticar pagamento</button>
    </div>)}
    {error && <p className="error">{error}</p>}
  </div>
}
