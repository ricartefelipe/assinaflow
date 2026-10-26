'use strict'

module.exports = async function checkDevelopCi({ github, context }) {
  const { owner, repo } = context.repo
  const branch = 'develop'
  const { data: initial } = await github.rest.repos.getBranch({ owner, repo, branch })
  const sha = initial.commit.sha
  if (!/^[a-f0-9]{40}$/.test(sha)) throw new Error('SHA de develop inválido.')

  const runs = []
  let total
  for (let page = 1; page <= 10; page++) {
    const { data } = await github.rest.actions.listWorkflowRuns({
      owner, repo, workflow_id: 'ci.yml', branch, event: 'push', head_sha: sha, per_page: 100, page,
    })
    if (!Number.isInteger(data.total_count) || data.total_count < 0 || data.total_count > 1000) {
      throw new Error('Não foi possível determinar com segurança a execução mais recente.')
    }
    if (total !== undefined && total !== data.total_count) throw new Error('Histórico de CI mudou durante a consulta.')
    total = data.total_count
    if (!Array.isArray(data.workflow_runs)) throw new Error('Resposta de CI inválida.')
    runs.push(...data.workflow_runs)
    if (runs.length >= total) break
    if (!data.workflow_runs.length) throw new Error('Histórico de CI incompleto.')
  }
  if (!runs.length) throw new Error(`Nenhuma execução push de ci.yml para develop em ${sha}.`)
  if (runs.length !== total || new Set(runs.map(run => run.id)).size !== runs.length) {
    throw new Error('Histórico de CI incompleto ou alterado durante a paginação.')
  }
  for (const run of runs) {
    if (run.head_sha !== sha || run.head_branch !== branch || run.event !== 'push') {
      throw new Error('A execução retornada não corresponde ao push de develop no SHA consultado.')
    }
    if (!Number.isSafeInteger(run.id) || !Number.isFinite(Date.parse(run.created_at))) {
      throw new Error('Identidade ou data da execução inválida.')
    }
  }
  runs.sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at) || b.id - a.id)
  const latest = runs[0]
  if (latest.status !== 'completed' || latest.conclusion !== 'success') {
    throw new Error(`CI mais recente para ${sha}: ${latest.status}/${latest.conclusion || 'sem conclusão'}.`)
  }

  // Read the current attempt too, so a rerun cannot reuse a stale successful response.
  const { data: current } = await github.rest.actions.getWorkflowRun({ owner, repo, run_id: latest.id })
  if (current.id !== latest.id || current.workflow_id !== latest.workflow_id || current.run_attempt !== latest.run_attempt ||
      current.head_sha !== sha || current.head_branch !== branch || current.event !== 'push' ||
      current.status !== 'completed' || current.conclusion !== 'success') {
    throw new Error('A execução ou tentativa de CI mudou ou não está concluída com success.')
  }
  const { data: final } = await github.rest.repos.getBranch({ owner, repo, branch })
  if (final.commit.sha !== sha) throw new Error('develop mudou durante a verificação; execute novamente para o novo SHA.')

  const base = `${context.serverUrl}/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`
  return { sha, commitUrl: `${base}/commit/${sha}`, runUrl: current.html_url, prUrl: `${base}/compare/master...develop?expand=1` }
}
