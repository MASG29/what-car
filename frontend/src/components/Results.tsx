import { useState } from 'react'
import type { Ranking } from '../api'
import { adFacts, ResultCard } from './ResultCard'
import { Source } from './Source'

/**
 * Ranked ads in two tabs: the normal list (top 5 first, highlighted) and the suspect ones (price well below
 * comparable ads). Each card is a closed box that opens on click. Excluded ads follow, collapsed.
 */
export function Results({ ranking }: { ranking: Ranking }) {
  const [tab, setTab] = useState<'ok' | 'suspect'>('ok')
  const ok = ranking.ranked.filter((s) => !s.suspect)
  const suspects = ranking.ranked.filter((s) => s.suspect)
  const shown = tab === 'ok' ? ok : suspects
  return (
    <section>
      <h2>
        Resultados: {ranking.ranked.length} anúncios ordenados, {ranking.excluded.length} excluídos
      </h2>
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'ok'} onClick={() => setTab('ok')}>
          Resultados ({ok.length})
        </button>
        <button role="tab" aria-selected={tab === 'suspect'} onClick={() => setTab('suspect')}>
          Suspeitos ({suspects.length})
        </button>
      </div>
      {tab === 'suspect' && (
        <p className="panel warn">
          Um anúncio é suspeito quando o preço está muito abaixo (80% ou menos) da mediana de anúncios comparáveis
          (mesmo modelo, anos e km próximos). Pode ser um bom negócio, mas muitas vezes esconde algo que o texto não
          diz: danos que só se veem nas fotos, problemas mecânicos, papéis em falta ou um anúncio falso. A aplicação
          não analisa fotos, por isso baixa a confiança nestes anúncios e nunca os põe no Top 5. Vê as fotos com
          atenção e pede para ver o carro antes de pagar seja o que for.
        </p>
      )}
      {shown.length === 0 && (
        <p>{tab === 'ok' ? 'Nenhum anúncio passou os teus limites.' : 'Nenhum anúncio suspeito.'}</p>
      )}
      {shown.map((s, i) => (
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
