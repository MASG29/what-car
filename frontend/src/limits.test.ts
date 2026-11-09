import { expect, test } from 'vitest'
import { type LimitsForm, SHORTLIST, toSearchLimits, validateLimits } from './limits'

const costs = {
  paperworkEur: 105, ipoEur: 37.47, iucPre2007Eur: 40, iucPost2007Eur: 149,
  beltEur: 350, insuranceMinEur: 800, insuranceMaxEur: 1500,
}

const filled: LimitsForm = {
  maxPriceEur: '2500', maxKm: '300000', minYear: '2000', fuels: ['PETROL'],
  priceCap: 'ASKING', centre: 'Ponte de Lima', radiusKm: '65', minDoors: '', realCost: costs,
}

test('validateLimits_requiresPriceKmYearFuel', () => {
  expect(validateLimits(filled)).toEqual([])

  const empty = { ...filled, maxPriceEur: '', maxKm: ' ', minYear: '', fuels: [] }
  expect(validateLimits(empty)).toEqual([
    'Indica o preço máximo.',
    'Indica os km máximos.',
    'Indica o ano mínimo.',
    'Escolhe pelo menos um combustível.',
  ])

  expect(validateLimits({ ...filled, maxPriceEur: '0', maxKm: '-5', minYear: '20.5' })).toHaveLength(3)
  expect(validateLimits({ ...filled, realCost: { ...costs, beltEur: Number.NaN } }))
    .toEqual(['Os custos têm de ser números iguais ou maiores que zero.'])
})

test('minDoors_optionalButPositive', () => {
  expect(validateLimits({ ...filled, minDoors: '' })).toEqual([])
  expect(validateLimits({ ...filled, minDoors: ' ' })).toEqual([])
  expect(validateLimits({ ...filled, minDoors: '5' })).toEqual([])
  expect(validateLimits({ ...filled, minDoors: '0' })).toEqual(['O mínimo de portas tem de ser um número maior que zero.'])
  expect(validateLimits({ ...filled, minDoors: 'x' })).toHaveLength(1)
  // forms saved before the field existed have no minDoors
  const old = { ...filled, minDoors: undefined } as unknown as LimitsForm
  expect(validateLimits(old)).toEqual([])
  expect(toSearchLimits(old).minDoors).toBeNull()

  expect(toSearchLimits(filled).minDoors).toBeNull()
  expect(toSearchLimits({ ...filled, minDoors: '5' }).minDoors).toBe(5)
})

test('models_defaultToReportShortlist_allBcSendsEmpty', () => {
  expect(toSearchLimits(filled).models).toEqual(SHORTLIST)
  expect(SHORTLIST).toContain('Mitsubishi Colt')
  expect(SHORTLIST).not.toContain('Renault Clio')
  expect(toSearchLimits({ ...filled, allBC: true }).models).toEqual([])
})

test('validateLimits_requiresCentreAndRadius', () => {
  expect(validateLimits({ ...filled, centre: ' ', radiusKm: '' })).toEqual([
    'Indica o centro da pesquisa.',
    'Indica o raio em km.',
  ])
  expect(validateLimits({ ...filled, radiusKm: '0' })).toEqual(['Indica o raio em km.'])
  expect(validateLimits({ ...filled, radiusKm: '6.5' })).toEqual(['Indica o raio em km.'])
})

test('toSearchLimits_sendsTrimmedCentreAndNumericRadius', () => {
  const l = toSearchLimits({ ...filled, centre: '  Ponte de Lima ', radiusKm: '65' })
  expect(l.centre).toBe('Ponte de Lima')
  expect(l.radiusKm).toBe(65)
  expect('districts' in l).toBe(false)
})

test('oldSavedFormWithDistricts_validatesWithoutThrowing', () => {
  const old = { ...filled, districts: 'Braga', centre: undefined, radiusKm: undefined } as unknown as LimitsForm
  expect(validateLimits(old)).toEqual(['Indica o centro da pesquisa.', 'Indica o raio em km.'])
})
