import { afterEach, expect, test, vi } from 'vitest'
import { deleteImport, formatInt, getRun, pct, pollFailure, startSearch } from './api'

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

test('formatInt_separatorOnlyFromTenThousand', () => {
  expect(formatInt(9500)).toBe('9500')
  expect(formatInt(10000)).toBe('10\u00a0000')
  expect(formatInt(169000)).toBe('169\u00a0000')
  expect(formatInt(1234567)).toBe('1\u00a0234\u00a0567')
})

test('pct_usesPtFormWithSpaceBeforePercent', () => {
  expect(pct(0.8)).toBe('80\u00a0%')
})

test('startSearch_400WithSuggestion_carriesIt', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(
    JSON.stringify({ messagePt: 'Marca e modelo desconhecidos: Ford Fiesa. Será que querias dizer «Ford Fiesta»?', suggestion: 'Ford Fiesta' }),
    { status: 400 })))
  const err = await startSearch({} as never).catch((e) => e)
  expect(err.status).toBe(400)
  expect(err.suggestion).toBe('Ford Fiesta')
})

test('deleteImport_sendsDeleteToImportId', async () => {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify({ deleted: 3 }), { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
  expect(await deleteImport(7)).toEqual({ deleted: 3 })
  expect(fetchMock).toHaveBeenCalledWith('/api/imports/7', { method: 'DELETE' })
})
