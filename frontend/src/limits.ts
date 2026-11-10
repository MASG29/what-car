import type { SearchLimits } from './api'

export const FUELS = ['PETROL', 'DIESEL', 'HYBRID', 'LPG', 'ELECTRIC', 'OTHER'] as const
export type Fuel = (typeof FUELS)[number]

export type RealCostSettings = {
  paperworkEur: number
  ipoEur: number
  iucPre2007Eur: number
  iucPost2007Eur: number
  beltEur: number
  insuranceMinEur: number
  insuranceMaxEur: number
}

/** What the form holds: the three limits stay text until sent, so an empty field is visibly empty. */
export type LimitsForm = {
  maxPriceEur: string
  maxKm: string
  minYear: string
  fuels: Fuel[]
  priceCap: 'ASKING' | 'REAL_COST'
  /** a locality, "Name" or "Name, District"; absent in forms saved before the centre existed */
  centre: string
  radiusKm: string
  /** optional; absent in forms saved before the field existed */
  minDoors: string
  /** keep ads that need repair, with a warning; absent in forms saved before the field existed */
  showDamaged?: boolean
  /** true = every B/C model in the knowledge base; absent in forms saved before the field existed */
  allBC?: boolean
  /** typed "make model"; empty = none; absent in forms saved before the field existed */
  makeModel?: string
  realCost: RealCostSettings
}

/** The research report's shortlist (spec, "Search scope"): the default model scope. */
export const SHORTLIST = [
  'Mitsubishi Colt', 'Honda Jazz', 'Toyota Yaris', 'Mazda 2', 'Ford Fiesta', 'Ford Fusion',
  'Volkswagen Polo', 'Volkswagen Golf', 'Toyota Corolla', 'Toyota Auris', 'Honda Civic', 'Mazda 3',
]

const positiveInt = (s: string) => /^\d+$/.test(s.trim()) && Number(s) > 0

/** PT messages; any message disables "Pesquisar". No field has a default. */
export function validateLimits(form: LimitsForm): string[] {
  const errors: string[] = []
  if (!positiveInt(form.maxPriceEur)) errors.push('Indica o preço máximo.')
  if (!positiveInt(form.maxKm)) errors.push('Indica os km máximos.')
  if (!positiveInt(form.minYear)) errors.push('Indica o ano mínimo.')
  if ((form.centre ?? '').trim() === '') errors.push('Indica o centro da pesquisa.')
  if (!positiveInt(form.radiusKm ?? '')) errors.push('Indica o raio em km.')
  if (form.fuels.length === 0) errors.push('Escolhe pelo menos um combustível.')
  const doors = (form.minDoors ?? '').trim()
  if (doors !== '' && !positiveInt(doors)) errors.push('O mínimo de portas tem de ser um número maior que zero.')
  if (Object.values(form.realCost).some((v) => !Number.isFinite(v) || v < 0)) {
    errors.push('Os custos têm de ser números iguais ou maiores que zero.')
  }
  return errors
}

export const FUEL_PT: Record<Fuel, string> = {
  PETROL: 'Gasolina', DIESEL: 'Gasóleo', HYBRID: 'Híbrido', LPG: 'GPL', ELECTRIC: 'Elétrico', OTHER: 'Outro',
}

/** Same values as RealCostSettings.defaults() on the server (research report). Editable, not limits. */
export const DEFAULT_REAL_COST: RealCostSettings = {
  paperworkEur: 105, ipoEur: 37.47, iucPre2007Eur: 40, iucPost2007Eur: 149,
  beltEur: 350, insuranceMinEur: 800, insuranceMaxEur: 1500,
}

/** Price, km, year, fuel, centre and radius start empty: no hidden defaults. */
export const emptyForm = (): LimitsForm => ({
  maxPriceEur: '', maxKm: '', minYear: '', fuels: [], priceCap: 'ASKING',
  centre: '', radiusKm: '', minDoors: '', showDamaged: false, realCost: { ...DEFAULT_REAL_COST },
})

/** Only call with a form that validateLimits accepts. Empty doors = any. */
export const toSearchLimits = (f: LimitsForm): SearchLimits => ({
  maxPriceEur: Number(f.maxPriceEur), maxKm: Number(f.maxKm), minYear: Number(f.minYear),
  fuels: f.fuels, priceCap: f.priceCap, models: f.allBC ? [] : SHORTLIST, realCost: f.realCost,
  centre: (f.centre ?? '').trim(), radiusKm: Number(f.radiusKm),
  minDoors: (f.minDoors ?? '').trim() === '' ? null : Number(f.minDoors),
  showDamaged: f.showDamaged ?? false,
  makeModel: (f.makeModel ?? '').trim() || null,
})
