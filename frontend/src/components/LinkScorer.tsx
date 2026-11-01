import { type FormEvent, useState } from 'react'
import { type Ranking, type Scoring, scoreLink, type SearchLimits } from '../api'
import { ResultCard } from './ResultCard'

type Props = { scoring: Scoring; limits: SearchLimits | null }

/** Paste one ad link and score it. Without valid limits the ad is scored without the user's caps. */
export function LinkScorer({ scoring, limits }: Props) {
  const [url, setUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<Ranking | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setResult(null)
    try {
      setResult(await scoreLink({ ...scoring, url, limits }))
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel">
      <h2>Avaliar um anúncio</h2>
      <form onSubmit={submit} className="row">
        <input
          type="url"
          placeholder="https://www.standvirtual.com/carros/anuncio/…"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
        />
        <button disabled={busy || !url.trim()}>{busy ? 'A avaliar…' : 'Avaliar'}</button>
      </form>
      {!limits && <p className="muted">Sem limites preenchidos: o anúncio é avaliado sem os teus limites.</p>}
      {error && <p className="error">{error}</p>}
      {result?.ranked.map((s) => (
        <ResultCard key={s.listing.url} s={s} position={1} />
      ))}
      {result?.excluded.map((x) => (
        <div key={x.listing.url} className="banner failed">
          <strong>Este anúncio foi excluído:</strong>
          <ul>
            {x.reasonsPt.map((r) => (
              <li key={r}>{r}</li>
            ))}
          </ul>
        </div>
      ))}
    </section>
  )
}
