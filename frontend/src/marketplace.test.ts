import { describe, expect, it } from 'vitest'
import { emptyForm } from './limits'
import { importedAgoPt, importSummaryPt, marketplaceSearchUrl } from './marketplace'

describe('marketplaceSearchUrl', () => {
  it('holds the max price only (Facebook ignores year and km in the link)', () => {
    const url = new URL(marketplaceSearchUrl({ ...emptyForm(), maxPriceEur: '2500', minYear: '2005', maxKm: '200000' }))
    expect(url.origin + url.pathname).toBe('https://www.facebook.com/marketplace/category/vehicles')
    expect(url.searchParams.get('maxPrice')).toBe('2500')
    expect(url.searchParams.has('minYear')).toBe(false)
    expect(url.searchParams.has('maxMileage')).toBe(false)
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
})
