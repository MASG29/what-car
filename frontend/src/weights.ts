import type { Weights } from './api'

/** Slider values -> weights that sum to 1. All zeros (or nothing positive) -> equal weights. */
export function normaliseWeights(sliders: number[]): number[] {
  const xs = sliders.map((x) => Math.max(0, x))
  const sum = xs.reduce((a, b) => a + b, 0)
  return sum > 0 ? xs.map((x) => x / sum) : xs.map(() => 1 / xs.length)
}

/** Slider order is CRITERIA order: price, reliability, consumption, completeness, photos. */
export function toWeights(sliders: number[]): Weights {
  const [price, reliability, consumption, completeness, photos] = normaliseWeights(sliders)
  return { price, reliability, consumption, completeness, photos }
}
