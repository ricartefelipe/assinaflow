'use strict'
const { test } = require('node:test')
const assert = require('node:assert/strict')
const check = require('./check-develop-ci.cjs')
const sha = 'a'.repeat(40)
const other = 'b'.repeat(40)
const run = overrides => ({ id: 20, workflow_id: 7, run_attempt: 1, head_sha: sha, head_branch: 'develop', event: 'push',
  status: 'completed', conclusion: 'success', created_at: '2026-10-26T10:00:00Z', html_url: 'https://github.com/o/r/actions/runs/20', ...overrides })
function fixture({ runs = [run()], detail, finalSha = sha, pages } = {}) {
  let reads = 0
  const context = { repo: { owner: 'o', repo: 'r' }, serverUrl: 'https://github.com' }
  const github = { rest: {
    repos: { getBranch: async args => { assert.equal(args.branch, 'develop'); return { data: { commit: { sha: ++reads === 1 ? sha : finalSha } } } } },
    actions: {
      listWorkflowRuns: async args => {
        assert.equal(args.workflow_id, 'ci.yml'); assert.equal(args.event, 'push'); assert.equal(args.head_sha, sha)
        assert.equal(args.branch, 'develop'); assert.equal(args.status, undefined)
        return { data: pages ? pages[args.page - 1] : { total_count: runs.length, workflow_runs: runs } }
      },
      getWorkflowRun: async args => ({ data: detail || runs.find(r => r.id === args.run_id) }),
    },
  } }
  return { github, context }
}
test('aprova somente o SHA atual com CI push concluída com success e oferece link manual', async () => {
  const result = await check(fixture())
  assert.equal(result.sha, sha)
  assert.equal(result.prUrl, 'https://github.com/o/r/compare/master...develop?expand=1')
})
test('recusa execução ausente', async () => assert.rejects(check(fixture({ runs: [] })), /Nenhuma execução/))
for (const status of ['queued', 'in_progress', 'waiting', 'pending', 'requested']) {
  test(`recusa CI ${status}`, async () => assert.rejects(check(fixture({ runs: [run({ status, conclusion: null })] })), /CI mais recente/))
}
for (const conclusion of ['failure', 'cancelled', 'timed_out', 'skipped', 'neutral', 'action_required', null]) {
  test(`recusa conclusão ${conclusion}`, async () => assert.rejects(check(fixture({ runs: [run({ conclusion })] })), /CI mais recente/))
}
for (const mismatch of [{ head_sha: other }, { event: 'pull_request' }, { head_branch: 'master' }]) {
  test(`recusa execução fora do filtro ${JSON.stringify(mismatch)}`, async () => assert.rejects(check(fixture({ runs: [run(mismatch)] })), /não corresponde/))
}
test('não reaproveita sucesso antigo quando a execução mais recente falhou', async () => {
  await assert.rejects(check(fixture({ runs: [run({ id: 10, created_at: '2026-10-25T10:00:00Z' }), run({ conclusion: 'failure' })] })), /CI mais recente/)
})
test('não reaproveita sucesso antigo quando a execução mais recente está pendente', async () => {
  await assert.rejects(check(fixture({ runs: [run({ id: 10, created_at: '2026-10-25T10:00:00Z' }), run({ status: 'queued', conclusion: null })] })), /CI mais recente/)
})
test('seleciona a execução mais recente sem depender da ordem de resposta', async () => {
  const result = await check(fixture({ runs: [run(), run({ id: 10, conclusion: 'failure', created_at: '2026-10-25T10:00:00Z' })] }))
  assert.equal(result.sha, sha)
})
test('recusa SHA de develop alterado antes de concluir', async () => assert.rejects(check(fixture({ finalSha: other })), /develop mudou/))
for (const changed of [{ run_attempt: 2 }, { status: 'in_progress', conclusion: null }, { head_sha: other }, { workflow_id: 8 }]) {
  test(`recusa mudança no detalhe da execução ${JSON.stringify(changed)}`, async () => {
    await assert.rejects(check(fixture({ detail: run(changed) })), /execução ou tentativa/)
  })
}
test('pagina o histórico e valida o mais recente entre páginas', async () => {
  const result = await check(fixture({ pages: [
    { total_count: 2, workflow_runs: [run({ id: 10, conclusion: 'failure', created_at: '2026-10-25T10:00:00Z' })] },
    { total_count: 2, workflow_runs: [run()] },
  ] }))
  assert.equal(result.sha, sha)
})
test('recusa paginação alterada', async () => {
  await assert.rejects(check(fixture({ pages: [
    { total_count: 2, workflow_runs: [run()] }, { total_count: 1, workflow_runs: [] },
  ] })), /Histórico de CI mudou/)
})
test('propaga erro de API sem aprovar', async () => {
  const f = fixture(); f.github.rest.actions.listWorkflowRuns = async () => { throw new Error('API unavailable') }
  await assert.rejects(check(f), /API unavailable/)
})
