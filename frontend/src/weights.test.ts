import { expect, test } from 'vitest'
import { normaliseWeights } from './weights'

const sum = (xs: number[]) => xs.reduce((a, b) => a + b, 0)

test('normaliseWeights_sumsToOne', () => {
  const w = normaliseWeights([30, 30, 15, 15, 10])
  expect(sum(w)).toBeCloseTo(1)
  expect(w[0]).toBeCloseTo(0.3)
  expect(w[4]).toBeCloseTo(0.1)
})

test('normaliseWeights_allZero_equal', () => {
  expect(normaliseWeights([0, 0, 0, 0, 0])).toEqual([0.2, 0.2, 0.2, 0.2, 0.2])
})
