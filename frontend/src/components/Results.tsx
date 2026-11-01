import type { Ranking } from '../api'
import { adFacts, ResultCard } from './ResultCard'
import { Source } from './Source'

/** Ranked ads (top 5 first, highlighted) and every excluded ad with its reasons, collapsed. */
export function Results({ ranking }: { ranking: Ranking }) {
  return (
    <section>
      <h2>
        Resultados: {ranking.ranked.length} anúncios ordenados, {ranking.excluded.length} excluídos
      </h2>
      {ranking.ranked.length === 0 && <p>Nenhum anúncio passou os teus limites.</p>}
      {ranking.ranked.map((s, i) => (
        <ResultCard key={s.listing.url} s={s} position={i + 1} />
      ))}
      <Excluded ranking={ranking} />
    </section>
  )
}

export function Excluded({ ranking }: { ranking: Ranking }) {
  if (ranking.excluded.length === 0) return null
  return (
    <details className="panel">
      <summary>Excluídos ({ranking.excluded.length})</summary>
      <ul>
        {Object.entries(ranking.excludedByReason).map(([reason, n]) => (
          <li key={reason}>
            {reason}: {n}
          </li>
        ))}
      </ul>
      <ul>
        {ranking.excluded.map((e) => (
          <li key={e.listing.url}>
            <Source url={e.listing.url} label={e.listing.title} /> — {adFacts(e.listing)}
            <div className="muted">{e.reasonsPt.join('; ')}</div>
          </li>
        ))}
      </ul>
    </details>
  )
}
