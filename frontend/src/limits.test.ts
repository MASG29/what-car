import { expect, test } from 'vitest'
import { type LimitsForm, validateLimits } from './limits'

const costs = {
  paperworkEur: 105, ipoEur: 37.47, iucPre2007Eur: 40, iucPost2007Eur: 149,
  beltEur: 350, insuranceMinEur: 800, insuranceMaxEur: 1500,
}

const filled: LimitsForm = {
  maxPriceEur: '2500', maxKm: '300000', minYear: '2000', fuels: ['PETROL'],
  priceCap: 'ASKING', districts: '', realCost: costs,
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
