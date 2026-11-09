import type { LimitsForm } from '../limits'

type Props = { form: LimitsForm; onChange: (patch: Partial<LimitsForm>) => void }

/** Centre + radius: required, no defaults. Forms saved before this field existed have neither. */
export function LocationFields({ form, onChange }: Props) {
  return (
    <fieldset>
      <legend>Localização</legend>
      <div className="grid">
        <label>
          Centro
          <input
            value={form.centre ?? ''}
            placeholder="Ponte de Lima"
            onChange={(e) => onChange({ centre: e.target.value })}
          />
        </label>
        <label>
          Raio (km)
          <input inputMode="numeric" value={form.radiusKm ?? ''} onChange={(e) => onChange({ radiusKm: e.target.value })} />
        </label>
      </div>
      <p className="muted">
        Se houver duas localidades com o mesmo nome, escreve «Nome, Distrito». Usa o mesmo centro e raio no filtro
        do Facebook.
      </p>
    </fieldset>
  )
}
