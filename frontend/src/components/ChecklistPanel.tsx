import type { Scored } from '../api'
import { Source } from './Source'

export function ChecklistPanel({ checklist }: { checklist: Scored['checklist'] }) {
  return (
    <details>
      <summary>Antes de comprar</summary>
      <ul>
        {checklist.links.map((l) => (
          <li key={l.url}>
            <Source url={l.url} label={l.labelPt} />
          </li>
        ))}
      </ul>
      <p>Perguntas ao vendedor:</p>
      <ul>
        {checklist.questionsPt.map((q) => (
          <li key={q}>{q}</li>
        ))}
      </ul>
    </details>
  )
}
