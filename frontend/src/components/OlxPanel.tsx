import { importOlx } from '../api'
import { HarUpload } from './HarUpload'
import { type HelpStep, HelpSteps } from './HelpSteps'

const SEARCH = 'https://www.olx.pt/carros-motos-e-barcos/carros/'

/** Text only: the DevTools screenshots in the Marketplace guide show Facebook's requests. */
const STEPS: HelpStep[] = [
  { text: 'Carrega em "Abrir pesquisa no OLX" e aplica no OLX os teus filtros (preço, localização).' },
  { text: 'Carrega em F12 e abre o separador "Rede" ("Network").' },
  {
    text: 'Liga os filtros "Doc" e "Fetch/XHR" (Ctrl+clique para escolher os dois) e marca "Manter registo" ("Preserve log").',
  },
  {
    text:
      'Carrega em F5 (sem isto falta a primeira página e as marcas dos carros) e depois passa 3 a 5 páginas ' +
      'pelos números de página no fundo da lista. A primeira linha da lista de pedidos deve ser a própria página (Doc).',
  },
  { text: 'Grava o ficheiro: botão direito na lista e "Guardar tudo como HAR" ("Save all as HAR").' },
  { text: 'Carrega em "Importar HAR" aqui e escolhe o ficheiro que gravaste.' },
]

/** OLX blocks automatic reading, so the user browses it and saves a HAR; nothing is requested from OLX. */
export function OlxPanel({ disabled }: { disabled: boolean }) {
  return (
    <div className="subpanel">
      <p className="muted">O OLX bloqueia a leitura automática: os anúncios entram por um ficheiro HAR gravado por ti.</p>
      <p>
        <a href={SEARCH} target="_blank" rel="noreferrer noopener">
          Abrir pesquisa no OLX
        </a>
      </p>
      <HelpSteps
        steps={STEPS}
        warning="O ficheiro HAR tem a tua sessão do OLX: não o partilhes e apaga-o depois de importar."
      />
      <HarUpload send={importOlx} disabled={disabled} />
    </div>
  )
}
