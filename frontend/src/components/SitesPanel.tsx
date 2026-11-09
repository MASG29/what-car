import type { Site } from '../api'
import type { LimitsForm } from '../limits'
import { MarketplacePanel } from './MarketplacePanel'

const LABELS: Record<Site, string> = {
  STANDVIRTUAL: 'Standvirtual',
  MARKETPLACE: 'Marketplace (anúncios importados)',
}

export function SitesPanel({
  sites,
  onChange,
  form,
  running,
}: {
  sites: Site[]
  onChange: (s: Site[]) => void
  form: LimitsForm
  running: boolean
}) {
  const toggle = (s: Site) => onChange(sites.includes(s) ? sites.filter((x) => x !== s) : [...sites, s])
  return (
    <section className="panel">
      <h2>Sites</h2>
      {(Object.keys(LABELS) as Site[]).map((s) => (
        <label className="inline" key={s}>
          <input type="checkbox" checked={sites.includes(s)} onChange={() => toggle(s)} />
          {LABELS[s]}
        </label>
      ))}
      {sites.includes('MARKETPLACE') && <MarketplacePanel form={form} disabled={running} />}
    </section>
  )
}
