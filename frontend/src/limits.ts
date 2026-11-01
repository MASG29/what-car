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
  districts: string
  /** optional; absent in forms saved before the field existed */
  minDoors: string
  /** true = every B/C model in the knowledge base; absent in forms saved before the field existed */
  allBC?: boolean
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

/** Price, km, year and fuel start empty. Districts start with the spec's default region. */
export const emptyForm = (): LimitsForm => ({
  maxPriceEur: '', maxKm: '', minYear: '', fuels: [], priceCap: 'ASKING',
  districts: 'Viana do Castelo, Braga, Porto', minDoors: '', realCost: { ...DEFAULT_REAL_COST },
})

/** Only call with a form that validateLimits accepts. Empty districts = every district; empty doors = any. */
export const toSearchLimits = (f: LimitsForm): SearchLimits => ({
  maxPriceEur: Number(f.maxPriceEur), maxKm: Number(f.maxKm), minYear: Number(f.minYear),
  fuels: f.fuels, priceCap: f.priceCap, models: f.allBC ? [] : SHORTLIST, realCost: f.realCost,
  districts: f.districts.split(',').map((d) => d.trim()).filter(Boolean),
  minDoors: (f.minDoors ?? '').trim() === '' ? null : Number(f.minDoors),
})
