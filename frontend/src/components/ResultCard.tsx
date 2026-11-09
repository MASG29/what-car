import { type Ad, CRITERIA, CRITERION_PT, formatEur, formatInt, pct, type Scored } from '../api'
import { FUEL_PT } from '../limits'
import { importedAgoPt } from '../marketplace'
import { ChecklistPanel } from './ChecklistPanel'
import { RealCostPanel } from './RealCostPanel'
import { Source } from './Source'

/** One line of facts; a missing fact says so instead of being left out. */
export function adFacts(l: Ad): string {
  return [
    l.priceEur != null ? formatEur(l.priceEur) : 'preço não indicado',
    l.year != null ? (l.month != null ? `${String(l.month).padStart(2, '0')}/${l.year}` : String(l.year)) : 'ano não indicado',
    l.km != null ? `${formatInt(l.km)} km` : 'km não indicados',
    l.fuel ? FUEL_PT[l.fuel] : null,
    l.cc ? `${l.cc} cm³` : null,
    l.powerCv ? `${l.powerCv} cv` : null,
    l.gearbox,
    [l.city, l.district].filter(Boolean).join(', ') || null,
    l.site === 'MARKETPLACE' ? importedAgoPt(l.lastSeen) : null,
  ]
    .filter(Boolean)
    .join(' · ')
}

export function ResultCard({ s, position }: { s: Scored; position: number }) {
  const l = s.listing
  return (
    <article className={s.top5 ? 'card top5' : 'card'}>
      <header>
        <h3>
          {position}. <Source url={l.url} label={l.title} />
        </h3>
        <p>{s.whyPt}</p>
        <p>{adFacts(l)}</p>
        <p className="score">
          Pontuação {pct(s.total)} · confiança {pct(s.confidence)} · ordenação {pct(s.rank)}
          {s.top5 && <span className="badge">Top 5</span>}
        </p>
      </header>

      <table className="subs">
        <thead>
          <tr>
            <th>Critério</th>
            <th>Valor</th>
            <th>Confiança</th>
            <th>Porquê</th>
          </tr>
        </thead>
        <tbody>
          {CRITERIA.filter((c) => s.subs[c]).map((c) => {
            const sub = s.subs[c]!
            return (
              <tr key={c}>
                <td>{CRITERION_PT[c]}</td>
                <td className="num">{sub.value == null ? <em>sem dados</em> : pct(sub.value)}</td>
                <td className="num">{pct(sub.confidence)}</td>
                <td>
                  {sub.explanationPt}
                  {/* sub warnings are not repeated here: the card's warningsPt already holds all of them */}
                  {sub.sourceUrls.length > 0 && (
                    <div className="sources">
                      Fontes:{' '}
                      {sub.sourceUrls.map((u, i) => (
                        <span key={u}>
                          {i > 0 && ', '}
                          <Source url={u} label={`[${i + 1}]`} />
                        </span>
                      ))}
                    </div>
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>

      {s.warningsPt.length > 0 && (
        <ul className="warn">
          {s.warningsPt.map((w) => (
            <li key={w}>{w}</li>
          ))}
        </ul>
      )}
      {s.notesPt.length > 0 && (
        <ul className="notes">
          {s.notesPt.map((n) => (
            <li key={n}>{n}</li>
          ))}
        </ul>
      )}
      {s.alsoOnUrls.length > 0 && (
        <p>
          Também em:{' '}
          {s.alsoOnUrls.map((u, i) => (
            <span key={u}>
              {i > 0 && ', '}
              <Source url={u} />
            </span>
          ))}
        </p>
      )}
      <RealCostPanel cost={s.realCost} />
      <ChecklistPanel checklist={s.checklist} />
    </article>
  )
}
