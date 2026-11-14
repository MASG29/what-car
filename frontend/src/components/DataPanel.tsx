import { useState } from 'react'
import { deleteAllData, deleteImport, deleteSearches, formatInt, type ImportBatch, listImports, SITE_NAMES } from '../api'

const when = (iso: string) =>
  new Date(iso).toLocaleString('pt-PT', { day: 'numeric', month: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' })

/**
 * Stored imports and the delete actions. Each asks first. onCleared runs after saved searches are gone, so the app
 * also forgets the last run it reloads on start.
 */
export function DataPanel({ disabled, onCleared }: { disabled: boolean; onCleared: () => void }) {
  const [imports, setImports] = useState<ImportBatch[] | null>(null)
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null)

  const reload = async () => setImports(await listImports())

  async function act(question: string, action: () => Promise<{ deleted: number }>, done: (n: number) => string, clears: boolean) {
    if (!window.confirm(question)) return
    setMessage(null)
    try {
      const { deleted } = await action()
      if (clears) onCleared()
      setMessage({ text: done(deleted), error: false })
      await reload()
    } catch (e) {
      setMessage({ text: (e as Error).message, error: true })
    }
  }

  const ads = (n: number) => `${formatInt(n)} ${n === 1 ? 'anúncio apagado' : 'anúncios apagados'}.`

  return (
    <details
      className="panel"
      onToggle={(e) => {
        if (e.currentTarget.open) reload().catch((err: Error) => setMessage({ text: err.message, error: true }))
      }}
    >
      <summary>Dados guardados</summary>
      <h3>Importações</h3>
      {imports && imports.length === 0 && <p className="muted">Nenhuma importação guardada.</p>}
      <ul>
        {imports?.map((b) => (
          <li key={b.id}>
            {SITE_NAMES[b.site]}, {when(b.importedAt)} — {formatInt(b.read)} lidos, {formatInt(b.added)} novos, {formatInt(b.listings)} anúncios{' '}
            <button
              disabled={disabled}
              onClick={() =>
                act('Apagar esta importação e os anúncios que só ela tem? Não dá para desfazer.', () => deleteImport(b.id), ads, false)
              }
            >
              Apagar
            </button>
          </li>
        ))}
      </ul>
      <p>
        <button
          disabled={disabled}
          onClick={() =>
            act(
              'Apagar todas as pesquisas guardadas, incluindo a última? Os anúncios ficam. Não dá para desfazer.',
              deleteSearches,
              (n) => `${n} ${n === 1 ? 'pesquisa apagada' : 'pesquisas apagadas'}.`,
              true,
            )
          }
        >
          Apagar pesquisas guardadas
        </button>{' '}
        <button
          disabled={disabled}
          onClick={() =>
            act(
              'Apagar todos os dados: anúncios, importações e pesquisas? Os teus limites e pesos ficam. Não dá para desfazer.',
              deleteAllData,
              ads,
              true,
            )
          }
        >
          Apagar todos os dados
        </button>
      </p>
      {disabled && <p className="muted">Espera que a pesquisa acabe para apagar dados.</p>}
      {message && <p className={message.error ? 'error' : 'muted'}>{message.text}</p>}
    </details>
  )
}
