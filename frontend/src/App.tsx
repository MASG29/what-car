import { useEffect, useState } from 'react'
import { type ApiError, getResults, getRun, pollFailure, type Preset, type Ranking, type RunView, type Scoring, startSearch } from './api'
import { LinkScorer } from './components/LinkScorer'
import { PresetPicker } from './components/PresetPicker'
import { Results } from './components/Results'
import { SearchForm } from './components/SearchForm'
import { SitesPanel } from './components/SitesPanel'
import { StatusBanner } from './components/StatusBanner'
import { emptyForm, type LimitsForm, toSearchLimits, validateLimits } from './limits'
import { toWeights } from './weights'

const LAST_LIMITS = 'carfinder.lastLimits'
const SLIDERS = 'carfinder.customSliders'
const RUN_ID = 'carfinder.runId'
const POLL_MS = 2000

function load<T>(key: string): T | null {
  try {
    return JSON.parse(localStorage.getItem(key) ?? 'null')
  } catch {
    return null
  }
}

export default function App() {
  const [form, setForm] = useState<LimitsForm>(emptyForm)
  const [lastUsed, setLastUsed] = useState(() => load<LimitsForm>(LAST_LIMITS))
  const [preset, setPreset] = useState<Preset>('BALANCED')
  const [sliders, setSliders] = useState(() => load<number[]>(SLIDERS) ?? [30, 30, 15, 15, 10])
  const [runId, setRunId] = useState(() => localStorage.getItem(RUN_ID))
  const [run, setRun] = useState<RunView | null>(null)
  const [ranking, setRanking] = useState<Ranking | null>(null)
  const [error, setError] = useState<string | null>(null)

  const errors = validateLimits(form)
  const scoring: Scoring = { preset, customWeights: preset === 'CUSTOM' ? toWeights(sliders) : null }
  const running = run?.status === 'RUNNING' || run?.status === 'NEEDS_USER'

  // Poll the run until it ends, then load its results. The id survives a page reload.
  useEffect(() => {
    if (!runId) return
    let timer: number | undefined
    let stopped = false
    const tick = async () => {
      try {
        const r = await getRun(runId)
        if (stopped) return
        setRun(r)
        if (r.status === 'DONE') setRanking(await getResults(runId))
        else if (r.status !== 'FAILED') timer = window.setTimeout(tick, POLL_MS)
      } catch (e) {
        if (stopped) return
        // Drop the run so Pesquisar is enabled again.
        setRun(null)
        const f = pollFailure(e as ApiError)
        if (f.forgetRun) {
          localStorage.removeItem(RUN_ID)
          setRunId(null)
        }
        setError(f.messagePt)
      }
    }
    tick()
    return () => {
      stopped = true
      window.clearTimeout(timer)
    }
  }, [runId])

  async function search() {
    setError(null)
    setRanking(null)
    setRun(null)
    try {
      const { id } = await startSearch({ ...scoring, limits: toSearchLimits(form), sites: ['STANDVIRTUAL'] })
      localStorage.setItem(LAST_LIMITS, JSON.stringify(form))
      localStorage.setItem(RUN_ID, id)
      setLastUsed(form)
      setRunId(id)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  function changeSliders(s: number[]) {
    setSliders(s)
    localStorage.setItem(SLIDERS, JSON.stringify(s))
  }

  return (
    <main>
      <h1>Procura de carro usado</h1>
      <SearchForm form={form} onChange={setForm} lastUsed={lastUsed} />
      <PresetPicker preset={preset} sliders={sliders} onPreset={setPreset} onSliders={changeSliders} />
      <SitesPanel />

      <div className="actions">
        <button className="primary" disabled={errors.length > 0 || running} onClick={search}>
          Pesquisar
        </button>
        {errors.length > 0 && (
          <ul className="error">
            {errors.map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
        )}
      </div>

      {error && <p className="error">{error}</p>}
      {run && <StatusBanner run={run} />}
      {ranking && <Results ranking={ranking} />}

      <LinkScorer scoring={scoring} limits={errors.length === 0 ? toSearchLimits(form) : null} />
    </main>
  )
}
