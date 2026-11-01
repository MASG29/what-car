import { afterEach, expect, test, vi } from 'vitest'
import { getRun, pollFailure } from './api'

afterEach(() => vi.unstubAllGlobals())

test('getRun_404_carriesStatus_andIsClearedSilently', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 404 })))
  const e = await getRun('stale').catch((x) => x)
  expect(e.status).toBe(404)
  expect(pollFailure(e)).toEqual({ forgetRun: true, messagePt: null })
})

test('getRun_networkError_givesPtMessage_andKeepsRun', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))))
  const e = await getRun('x').catch((x) => x)
  expect(pollFailure(e)).toEqual({
    forgetRun: false,
    messagePt: 'Não foi possível contactar o servidor. A aplicação está a correr?',
  })
})
