import type { Ad, Ranking, Site } from './api'
import type { LimitsForm } from './limits'

/**
 * Facebook's vehicles search, opened by the user in their own browser. Only maxPrice and query (the typed make and
 * model) are honoured by the link (checked by hand on 2026-11-09/10); year and km are filtered by the app after the
 * import.
 */
export function marketplaceSearchUrl(form: LimitsForm): string {
  const url = new URL('https://www.facebook.com/marketplace/category/vehicles')
  if (form.maxPriceEur.trim()) url.searchParams.set('maxPrice', form.maxPriceEur.trim())
  const typed = (form.makeModel ?? '').trim()
  if (typed) url.searchParams.set('query', typed)
  url.searchParams.set('exact', 'false')
  return url.toString()
}

/** Ranked Marketplace ads whose page the app could not open (login wall or back-off), best first. */
export const unreadMarketplace = (r: Ranking): Ad[] =>
  r.ranked.filter((s) => s.listing.site === 'MARKETPLACE' && !s.listing.detailFetched).map((s) => s.listing)

/** skipped: another site's copies left out on import (OLX's Standvirtual ads); absent = 0 */
export type ImportResult = { read: number; added: number; unavailable: number; skipped?: number }

/** Sites whose ads come from HAR files the user imports. */
export const isImported = (s: Site) => s === 'MARKETPLACE' || s === 'OLX'

const DAY_MS = 24 * 60 * 60 * 1000

export function importedAgoPt(lastSeen: string, now: Date = new Date()): string {
  const days = Math.floor((now.getTime() - new Date(lastSeen).getTime()) / DAY_MS)
  if (days <= 0) return 'importado hoje'
  return `importado há ${days} ${days === 1 ? 'dia' : 'dias'}`
}

export function importSummaryPt(r: ImportResult): string {
  const parts = [
    `${r.read} ${r.read === 1 ? 'anúncio lido' : 'anúncios lidos'}`,
    `${r.added} ${r.added === 1 ? 'novo' : 'novos'}`,
  ]
  if (r.unavailable > 0) parts.push(`${r.unavailable} ${r.unavailable === 1 ? 'vendido ou reservado' : 'vendidos ou reservados'}`)
  if (r.skipped) parts.push(`${r.skipped} do Standvirtual, ${r.skipped === 1 ? 'ignorado' : 'ignorados'}`)
  return parts.join(', ') + '.'
}
