import { useState } from 'react'
import type { Site } from '../api'
import type { LimitsForm } from '../limits'
import { MarketplacePanel } from './MarketplacePanel'
import { OlxPanel } from './OlxPanel'

const LABELS: Record<Site, string> = {
  STANDVIRTUAL: 'Standvirtual',
  MARKETPLACE: 'Marketplace (anúncios importados)',
  CUSTOJUSTO: 'CustoJusto',
  OLX: 'OLX (anúncios importados)',
}

export function SitesPanel({
  sites,
  onChange,
  form,
  running,
  automated,
  onAutomated,
}: {
  sites: Site[]
  onChange: (s: Site[]) => void
  form: LimitsForm
  running: boolean
  automated: boolean
  onAutomated: (on: boolean) => void
}) {
  // ticking the box only shows the warning; a second click confirms
  const [confirming, setConfirming] = useState(false)
  const tick = () => {
    if (automated) onAutomated(false)
    else setConfirming(true)
  }
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
      {sites.includes('CUSTOJUSTO') && <p className="warn">Este site pode bloquear o teu IP se pesquisares muito.</p>}
      {sites.includes('MARKETPLACE') && (
        <>
          <label className="inline">
            <input type="checkbox" checked={automated || confirming} disabled={running} onChange={tick} />
            Pesquisar também automaticamente (conta secundária)
          </label>
          {confirming && (
            <p className="warn">
              Usa uma conta secundária: o Facebook pode restringir a conta.{' '}
              <button type="button" onClick={() => {
                  onAutomated(true)
                  setConfirming(false)
                }}>
                Confirmar
              </button>{' '}
              <button type="button" onClick={() => setConfirming(false)}>
                Cancelar
              </button>
            </p>
          )}
          {automated && (
            <p className="warn">Usa uma conta secundária: o Facebook pode restringir a conta.</p>
          )}
          <MarketplacePanel form={form} disabled={running} />
        </>
      )}
      {sites.includes('OLX') && <OlxPanel disabled={running} />}
    </section>
  )
}
