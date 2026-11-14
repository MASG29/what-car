import { describe, expect, it } from 'vitest'
import { emptyForm } from './limits'
import type { Ranking, Scored } from './api'
import { importedAgoPt, importSummaryPt, isImported, marketplaceSearchUrl, unreadMarketplace } from './marketplace'

describe('marketplaceSearchUrl', () => {
  it('holds the max price only (Facebook ignores year and km in the link)', () => {
    const url = new URL(marketplaceSearchUrl({ ...emptyForm(), maxPriceEur: '2500', minYear: '2005', maxKm: '200000' }))
    expect(url.origin + url.pathname).toBe('https://www.facebook.com/marketplace/category/vehicles')
    expect(url.searchParams.get('maxPrice')).toBe('2500')
    expect(url.searchParams.has('minYear')).toBe(false)
    expect(url.searchParams.has('maxMileage')).toBe(false)
  })
})

describe('marketplaceSearchUrl with a typed make and model', () => {
  it('adds the trimmed text as query, on the vehicles category', () => {
    const url = new URL(marketplaceSearchUrl({ ...emptyForm(), maxPriceEur: '2500', makeModel: ' ford fiesta ' }))
    expect(url.pathname).toBe('/marketplace/category/vehicles')
    expect(url.searchParams.get('query')).toBe('ford fiesta')
    expect(new URL(marketplaceSearchUrl({ ...emptyForm(), makeModel: '  ' })).searchParams.has('query')).toBe(false)
  })
})

describe('importedAgoPt', () => {
  const now = new Date('2026-11-09T12:00:00Z')
  it('counts whole days since the last import', () => {
    expect(importedAgoPt('2026-11-09T08:00:00Z', now)).toBe('importado hoje')
    expect(importedAgoPt('2026-11-08T11:00:00Z', now)).toBe('importado há 1 dia')
    expect(importedAgoPt('2026-10-30T12:00:00Z', now)).toBe('importado há 10 dias')
  })
})

describe('importSummaryPt', () => {
  it('says how many were read, new and sold', () => {
    expect(importSummaryPt({ read: 6, added: 4, unavailable: 2 })).toBe(
      '6 anúncios lidos, 4 novos, 2 vendidos ou reservados.',
    )
    expect(importSummaryPt({ read: 1, added: 1, unavailable: 0 })).toBe('1 anúncio lido, 1 novo.')
  })

  it('says how many Standvirtual copies were left out', () => {
    expect(importSummaryPt({ read: 145, added: 145, unavailable: 0, skipped: 54 })).toBe(
      '145 anúncios lidos, 145 novos, 54 do Standvirtual, ignorados.',
    )
    expect(importSummaryPt({ read: 3, added: 0, unavailable: 1, skipped: 1 })).toBe(
      '3 anúncios lidos, 0 novos, 1 vendido ou reservado, 1 do Standvirtual, ignorado.',
    )
  })
})

describe('isImported', () => {
  it('is true for the HAR-import sites only', () => {
    expect(isImported('MARKETPLACE')).toBe(true)
    expect(isImported('OLX')).toBe(true)
    expect(isImported('STANDVIRTUAL')).toBe(false)
    expect(isImported('CUSTOJUSTO')).toBe(false)
  })
})

describe('unreadMarketplace', () => {
  const scored = (site: 'MARKETPLACE' | 'STANDVIRTUAL', url: string, detailFetched: boolean, suspect = false) =>
    ({ listing: { site, url, detailFetched }, suspect }) as unknown as Scored
  it('lists Marketplace ads whose page was not read, in rank order, suspects included', () => {
    const ranking = {
      ranked: [
        scored('MARKETPLACE', 'a', false),
        scored('STANDVIRTUAL', 'b', false),
        scored('MARKETPLACE', 'c', true),
        scored('MARKETPLACE', 'd', false, true),
      ],
      excluded: [],
      excludedByReason: {},
    } as Ranking
    expect(unreadMarketplace(ranking).map((a) => a.url)).toEqual(['a', 'd'])
  })
})
