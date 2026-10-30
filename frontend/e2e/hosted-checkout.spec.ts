import { expect, test } from '@playwright/test'
import { hostedCheckoutUrl } from '../src/billing/hostedCheckout'

// Regressão do destino do redirect: só a origem exata do Checkout hospedado passa.
test.describe('hostedCheckoutUrl', () => {
  test('aceita a página hospedada do Stripe', () => {
    expect(hostedCheckoutUrl('https://checkout.stripe.com/c/pay/cs_test_1?x=1')).toBe('https://checkout.stripe.com/c/pay/cs_test_1?x=1')
  })

  for (const raw of [
    'http://checkout.stripe.com/c/pay/cs_test_1',
    'https://evil.checkout.stripe.com/c/pay/cs_test_1',
    'https://checkout.stripe.com.evil.example/c/pay/cs_test_1',
    'https://checkout.stripe.com:8443/c/pay/cs_test_1',
    'https://user:pass@checkout.stripe.com/c/pay/cs_test_1',
    'https://checkout.stripe.com@evil.example/c/pay/cs_test_1',
    'https://checkout.stripe.com%2F@evil.example/',
    '//checkout.stripe.com/c/pay/cs_test_1',
    '/c/pay/cs_test_1',
    'javascript:alert(1)',
    'https://checkout.stripe.com/c/pay/cs_test_1#frag',
    '',
    'not a url',
  ]) {
    test(`recusa ${raw || '(vazio)'}`, () => {
      expect(() => hostedCheckoutUrl(raw)).toThrow('Destino inválido.')
    })
  }
})
