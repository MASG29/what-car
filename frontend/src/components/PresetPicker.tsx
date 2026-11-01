import { CRITERIA, CRITERION_PT, type Preset } from '../api'
import { normaliseWeights } from '../weights'

const PRESET_PT: Record<Preset, string> = {
  BALANCED: 'Equilibrado',
  ECONOMICAL: 'Mais económico',
  RELIABLE: 'Mais fiável',
  CUSTOM: 'Personalizado',
}

type Props = {
  preset: Preset
  sliders: number[]
  onPreset: (p: Preset) => void
  onSliders: (s: number[]) => void
}

export function PresetPicker({ preset, sliders, onPreset, onSliders }: Props) {
  const weights = normaliseWeights(sliders)
  return (
    <section className="panel">
      <h2>Critério de ordenação</h2>
      {(Object.keys(PRESET_PT) as Preset[]).map((p) => (
        <label key={p} className="inline">
          <input type="radio" checked={preset === p} onChange={() => onPreset(p)} />
          {PRESET_PT[p]}
        </label>
      ))}
      {preset === 'CUSTOM' && (
        <div className="sliders">
          {CRITERIA.map((c, i) => (
            <label key={c}>
              <span>
                {CRITERION_PT[c]}: {Math.round(weights[i] * 100)}%
              </span>
              <input
                type="range"
                min={0}
                max={100}
                value={sliders[i]}
                onChange={(e) => onSliders(sliders.map((v, j) => (j === i ? Number(e.target.value) : v)))}
              />
            </label>
          ))}
        </div>
      )}
    </section>
  )
}
