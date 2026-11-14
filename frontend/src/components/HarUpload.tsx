import { useState } from 'react'
import { importSummaryPt, type ImportResult } from '../marketplace'

/** File picker + "Importar HAR"; the file goes to the server as is and the summary or PT error is shown. */
export function HarUpload({ send, disabled }: { send: (f: File) => Promise<ImportResult>; disabled: boolean }) {
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null)

  async function upload() {
    if (!file) return
    setBusy(true)
    setMessage(null)
    try {
      setMessage({ text: importSummaryPt(await send(file)), error: false })
    } catch (e) {
      setMessage({ text: (e as Error).message, error: true })
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <p>
        <input type="file" accept=".har,application/json" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <button disabled={!file || busy || disabled} onClick={upload}>
          {busy ? 'A importar…' : 'Importar HAR'}
        </button>
      </p>
      {message && <p className={message.error ? 'error' : 'muted'}>{message.text}</p>}
    </>
  )
}
