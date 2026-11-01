import { formatEur, type Scored } from '../api'

export function RealCostPanel({ cost }: { cost: Scored['realCost'] }) {
  return (
    <details>
      <summary>Custo real: {formatEur(cost.totalEur)}</summary>
      <table>
        <tbody>
          {Object.entries(cost.itemsPt).map(([label, eur]) => (
            <tr key={label}>
              <td>{label}</td>
              <td className="num">{formatEur(eur)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="muted">{cost.insuranceRangePt}</p>
    </details>
  )
}
