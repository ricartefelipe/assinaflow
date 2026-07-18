import { expect, test } from '@playwright/test'

test.describe('subscriber smoke', () => {
  test('cadastro, contratar, trocar plano e cancelar', async ({ page, request }) => {
    const api = process.env.E2E_API_URL || 'http://localhost:8080'
    let apiUp = false
    try {
      const health = await request.get(`${api}/actuator/health`)
      apiUp = health.ok()
    } catch {
      apiUp = false
    }
    test.skip(!apiUp, `API indisponivel em ${api}`)

    const email = `e2e.${Date.now()}@example.com`
    const senha = 'senha12345'

    await page.goto('/')
    await page.getByRole('link', { name: 'Assinar' }).click()
    await page.getByLabel('Nome').fill('E2E User')
    await page.getByLabel('E-mail').fill(email)
    await page.getByLabel('Senha').fill(senha)
    await page.getByRole('button', { name: 'Criar conta' }).click()

    await expect(page.getByRole('heading', { name: 'Contratar plano' })).toBeVisible({ timeout: 15_000 })
    await page.getByRole('button', { name: /Ativar/ }).click()

    await expect(page.getByRole('heading', { name: 'Minha assinatura' })).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('Ativa', { exact: true })).toBeVisible()

    await page.getByRole('link', { name: 'Trocar plano' }).click()
    await expect(page.getByRole('heading', { name: 'Trocar plano' })).toBeVisible()
    await page.getByRole('button', { name: /Mudar para/ }).click()

    await expect(page.getByRole('heading', { name: 'Minha assinatura' })).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('Ativa', { exact: true })).toBeVisible()

    page.once('dialog', (dialog) => dialog.accept())
    await page.getByRole('button', { name: 'Cancelar assinatura' }).click()
    await expect(page.getByText(/Cancelamento agendado/i)).toBeVisible({ timeout: 15_000 })
  })
})
