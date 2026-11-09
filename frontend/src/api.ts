import type { Fuel, RealCostSettings } from './limits'

export type Criterion = 'PRICE' | 'RELIABILITY' | 'CONSUMPTION' | 'COMPLETENESS' | 'PHOTOS'
export const CRITERIA: Criterion[] = ['PRICE', 'RELIABILITY', 'CONSUMPTION', 'COMPLETENESS', 'PHOTOS']
export const CRITERION_PT: Record<Criterion, string> = {
  PRICE: 'Preço',
  RELIABILITY: 'Fiabilidade',
  CONSUMPTION: 'Consumo',
  COMPLETENESS: 'Anúncio completo',
  PHOTOS: 'Fotos',
}

export type Preset = 'BALANCED' | 'ECONOMICAL' | 'RELIABLE' | 'CUSTOM'
export type Weights = Record<'price' | 'reliability' | 'consumption' | 'completeness' | 'photos', number>
export type Site = 'STANDVIRTUAL' | 'MARKETPLACE'

export type SearchLimits = {
  maxPriceEur: number
  priceCap: 'ASKING' | 'REAL_COST'
  maxKm: number
  minYear: number
  fuels: Fuel[]
  centre: string
  radiusKm: number
  models: string[]
  realCost: RealCostSettings
  /** null = any number of doors */
  minDoors: number | null
}

/** value null = "sem dados". */
export type Sub = {
  value: number | null
  confidence: number
  explanationPt: string
  sourceUrls: string[]
  warningsPt: string[]
}

export type Ad = {
  site: Site
  url: string
  title: string
  priceEur: number | null
  make: string | null
  model: string | null
  version: string | null
  year: number | null
  month: number | null
  km: number | null
  fuel: Fuel | null
  cc: number | null
  powerCv: number | null
  gearbox: string | null
  district: string | null
  city: string | null
  photoCount: number
  detailFetched: boolean
}

export type Scored = {
  listing: Ad
  alsoOnUrls: string[]
  subs: Partial<Record<Criterion, Sub>>
  total: number
  confidence: number
  rank: number
  top5: boolean
  warningsPt: string[]
  notesPt: string[]
  realCost: { totalEur: number; itemsPt: Record<string, number>; insuranceRangePt: string }
  checklist: { links: { labelPt: string; url: string }[]; questionsPt: string[] }
  /** PT sentence: position, strongest criteria, missing data, confidence, condition hints */
  whyPt: string
}

export type Ranking = {
  ranked: Scored[]
  excluded: { listing: Ad; reasonsPt: string[] }[]
  excludedByReason: Record<string, number>
}

export type RunStatus = 'RUNNING' | 'NEEDS_USER' | 'DONE' | 'FAILED'
export type RunView = { id: string; status: RunStatus; messagePt: string | null; collected: number }

export type Scoring = { preset: Preset; customWeights: Weights | null }

const GENERIC_ERROR = 'O pedido não foi aceite. Revê os dados e tenta de novo.'

export type ApiError = Error & { status?: number }

/** Errors carry the HTTP status and the server's messagePt when it sends one; Spring's default 400 body has none. */
async function call<T>(url: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(url, init)
  } catch {
    throw new Error('Não foi possível contactar o servidor. A aplicação está a correr?')
  }
  if (res.ok) return res.json()
  const body = await res.json().catch(() => null)
  const message = typeof body?.messagePt === 'string' && body.messagePt ? body.messagePt : GENERIC_ERROR
  throw Object.assign(new Error(message), { status: res.status })
}

const post = <T>(url: string, body: unknown) =>
  call<T>(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })

export const startSearch = (req: Scoring & { limits: SearchLimits; sites: Site[] }) =>
  post<{ id: string }>('/api/searches', req)
export const getRun = (id: string) => call<RunView>(`/api/searches/${id}`)
export const getResults = (id: string) => call<Ranking>(`/api/searches/${id}/results`)
/** A stored run id that 404s is stale (e.g. a new database): forget it silently. Anything else is shown. */
export const pollFailure = (e: ApiError) =>
  e.status === 404 ? { forgetRun: true, messagePt: null } : { forgetRun: false, messagePt: e.message }

export const scoreLink = (req: Scoring & { url: string; limits: SearchLimits | null }) =>
  post<Ranking>('/api/score-link', req)

/** Some sources are report paths, not URLs: only http(s) becomes a link. */
export const isHttp = (u: string) => /^https?:\/\//i.test(u)

const eur = new Intl.NumberFormat('pt-PT', { style: 'currency', currency: 'EUR', maximumFractionDigits: 2 })
export const formatEur = (n: number) => eur.format(n)
/** PT integer: no-break space between thousands, only from 10 000 ("9500", "169 000"). */
export const formatInt = (n: number) =>
  Math.abs(n) < 10000 ? String(n) : String(n).replace(/\B(?=(\d{3})+(?!\d))/g, '\u00a0')
export const pct = (x: number) => `${Math.round(x * 100)}\u00a0%`
