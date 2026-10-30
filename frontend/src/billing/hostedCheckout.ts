/**
 * Único destino aceito para o redirecionamento ao Checkout hospedado do Stripe:
 * origem exata https://checkout.stripe.com, sem porta explícita, sem credenciais na URL
 * e sem fragmento. A API já aplica a mesma regra; aqui ela falha fechada antes do redirect.
 */
export const HOSTED_CHECKOUT_ORIGIN = 'https://checkout.stripe.com'

export function hostedCheckoutUrl(raw: string): string {
  let url: URL
  try {
    url = new URL(raw)
  } catch {
    throw new Error('Destino inválido.')
  }
  const hosted =
    url.origin === HOSTED_CHECKOUT_ORIGIN &&
    url.username === '' &&
    url.password === '' &&
    url.hash === ''
  if (!hosted) throw new Error('Destino inválido.')
  return url.toString()
}
