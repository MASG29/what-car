import { useState } from 'react'
import { importMarketplace } from '../api'
import type { LimitsForm } from '../limits'
import { importSummaryPt, marketplaceSearchUrl } from '../marketplace'
import { HelpSteps } from './HelpSteps'

/** The user browses Marketplace and saves a HAR; the app only reads item pages of imported ads, logged out. */
export function MarketplacePanel({ form, disabled }: { form: LimitsForm; disabled: boolean }) {
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null)

  async function upload() {
    if (!file) return
    setBusy(true)
    setMessage(null)
    try {
      setMessage({ text: importSummaryPt(await importMarketplace(file)), error: false })
    } catch (e) {
      setMessage({ text: (e as Error).message, error: true })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="subpanel">
      <p>
        <a href={marketplaceSearchUrl(form)} target="_blank" rel="noreferrer noopener">
          Abrir pesquisa no Marketplace
        </a>
      </p>
      <HelpSteps />
      <p>
        <input type="file" accept=".har,application/json" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <button disabled={!file || busy || disabled} onClick={upload}>
          {busy ? 'A importar…' : 'Importar HAR'}
        </button>
      </p>
      {message && <p className={message.error ? 'error' : 'muted'}>{message.text}</p>}
    </div>
  )
}
