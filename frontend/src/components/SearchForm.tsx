import { LocationFields } from './LocationFields'
import { FUEL_PT, FUELS, type LimitsForm, type RealCostSettings, SHORTLIST } from '../limits'

const COST_PT: Record<keyof RealCostSettings, string> = {
  paperworkEur: 'Papelada (registo e transferência)',
  ipoEur: 'Inspeção (IPO)',
  iucPre2007Eur: 'IUC, matrícula antes de julho de 2007',
  iucPost2007Eur: 'IUC, matrícula a partir de julho de 2007',
  beltEur: 'Correia de distribuição (assumida em falta)',
  insuranceMinEur: 'Seguro anual, mínimo (fora do total)',
  insuranceMaxEur: 'Seguro anual, máximo (fora do total)',
}

type Props = {
  form: LimitsForm
  onChange: (f: LimitsForm) => void
  lastUsed: LimitsForm | null
}

export function SearchForm({ form, onChange, lastUsed }: Props) {
  const set = (patch: Partial<LimitsForm>) => onChange({ ...form, ...patch })
  const toggleFuel = (f: (typeof FUELS)[number]) =>
    set({ fuels: form.fuels.includes(f) ? form.fuels.filter((x) => x !== f) : [...form.fuels, f] })

  return (
    <section className="panel">
      <h2>Os teus limites</h2>
      {lastUsed && (
        <button type="button" className="link" onClick={() => onChange(lastUsed)}>
          Usar os últimos valores
        </button>
      )}
      <div className="grid">
        <label>
          Preço máximo (€)
          <input inputMode="numeric" value={form.maxPriceEur} onChange={(e) => set({ maxPriceEur: e.target.value })} />
        </label>
        <label>
          Km máximos
          <input inputMode="numeric" value={form.maxKm} onChange={(e) => set({ maxKm: e.target.value })} />
        </label>
        <label>
          Ano mínimo
          <input inputMode="numeric" value={form.minYear} onChange={(e) => set({ minYear: e.target.value })} />
        </label>
        <label>
          Mínimo de portas (opcional)
          <input inputMode="numeric" value={form.minDoors ?? ''} onChange={(e) => set({ minDoors: e.target.value })} />
        </label>
      </div>

      <fieldset>
        <legend>Combustível</legend>
        {FUELS.map((f) => (
          <label key={f} className="inline">
            <input type="checkbox" checked={form.fuels.includes(f)} onChange={() => toggleFuel(f)} />
            {FUEL_PT[f]}
          </label>
        ))}
      </fieldset>

      <fieldset>
        <legend>Modelos</legend>
        <label className="inline">
          <input type="checkbox" checked={form.allBC ?? false} onChange={(e) => set({ allBC: e.target.checked })} />
          Todos os B/C
        </label>
        <p className="muted">
          {form.allBC
            ? 'Todos os modelos dos segmentos B e C da base de conhecimento.'
            : `Lista do relatório: ${SHORTLIST.join(', ')}.`}
        </p>
      </fieldset>

      <LocationFields form={form} onChange={set} />

      <fieldset>
        <legend>Anúncios danificados</legend>
        <label className="inline">
          <input
            type="checkbox"
            checked={form.showDamaged ?? false}
            onChange={(e) => set({ showDamaged: e.target.checked })}
          />
          Mostrar carros que precisam de reparação
        </label>
        <p className="muted">
          Desligado, são excluídos. Ligado, aparecem com aviso e mais abaixo na lista; o custo da reparação não tem
          dados. Anúncios «para peças» são sempre excluídos.
        </p>
      </fieldset>

      <fieldset>
        <legend>O preço máximo aplica-se a</legend>
        <label className="inline">
          <input type="radio" checked={form.priceCap === 'ASKING'} onChange={() => set({ priceCap: 'ASKING' })} />
          Preço pedido
        </label>
        <label className="inline">
          <input type="radio" checked={form.priceCap === 'REAL_COST'} onChange={() => set({ priceCap: 'REAL_COST' })} />
          Custo real (preço + custos abaixo)
        </label>
      </fieldset>

      <details>
        <summary>Custos do "custo real" (editáveis)</summary>
        <div className="grid">
          {(Object.keys(COST_PT) as (keyof RealCostSettings)[]).map((k) => (
            <label key={k}>
              {COST_PT[k]} (€)
              <input
                type="number"
                min={0}
                step={k === 'ipoEur' ? '0.01' : '1'}
                value={Number.isNaN(form.realCost[k]) ? '' : form.realCost[k]}
                onChange={(e) => set({ realCost: { ...form.realCost, [k]: e.target.valueAsNumber } })}
              />
            </label>
          ))}
        </div>
      </details>
    </section>
  )
}
