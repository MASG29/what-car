import type { RunView } from '../api'

const STATUS_PT: Record<RunView['status'], string> = {
  RUNNING: 'A pesquisar…',
  NEEDS_USER: 'Precisa de ti',
  DONE: 'Pesquisa concluída',
  FAILED: 'A pesquisa falhou',
}

export function StatusBanner({ run }: { run: RunView }) {
  return (
    <div className={`banner ${run.status.toLowerCase()}`} role={run.status === 'NEEDS_USER' ? 'alert' : 'status'}>
      <strong>{STATUS_PT[run.status]}</strong>
      {run.messagePt && <span> — {run.messagePt}</span>}
      <span className="muted"> ({run.collected} anúncios recolhidos)</span>
    </div>
  )
}
