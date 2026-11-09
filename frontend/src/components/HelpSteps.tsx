/** Numbered PT-PT steps for recording a Marketplace HAR by hand. Screenshots have personal data cropped out. */
const STEPS: { text: string; image?: string }[] = [
  {
    text:
      'Carrega em "Abrir pesquisa no Marketplace". O Facebook abre já com o teu preço máximo; o ano e os km ' +
      'são filtrados aqui, depois de importares.',
  },
  {
    text:
      'No Facebook, em "Localização" ("Location"), põe o mesmo centro e raio que puseste aqui e carrega em ' +
      '"Aplicar" ("Apply").',
    image: 'help/step-2.png',
  },
  {
    text: 'Carrega em F12 e abre o separador "Network" ("Rede"). Faz isto antes de recarregar a página.',
    image: 'help/step-3.png',
  },
  {
    text:
      'Liga os filtros "Doc" e "Fetch/XHR" (Ctrl+clique para escolher os dois) e marca "Keep log" ' +
      '(nalgumas versões chama-se "Preserve log"; no Firefox, "Persist Logs" na roda dentada).',
    image: 'help/step-4.png',
  },
  {
    text:
      'Carrega em F5 e desce a página até teres anúncios suficientes: mantém a tecla Page Down (ou End) ' +
      'carregada. No Firefox também podes usar o deslocamento automático: liga-o em "Definições > Geral > ' +
      'Navegação > Usar deslocamento automático" (ou procura "deslocamento" nas Definições), depois clica com o ' +
      'botão do meio e move o rato para baixo; clica outra vez para parar.',
  },
  {
    text:
      'Grava o ficheiro HAR: no Chrome ou Brave, o botão da seta para baixo ("Export HAR") na barra do ' +
      'Network; ou botão direito na lista e "Save all as HAR".',
    image: 'help/step-6.png',
  },
  { text: 'Carrega em "Importar HAR" aqui e escolhe o ficheiro que gravaste.' },
]

export function HelpSteps() {
  return (
    <details>
      <summary>Como gravar o ficheiro HAR (passo a passo)</summary>
      <p className="warn">
        <strong>O ficheiro HAR tem a tua sessão do Facebook: não o partilhes e apaga-o depois de importar.</strong>
      </p>
      <ol>
        {STEPS.map((s) => (
          <li key={s.text}>
            <p>{s.text}</p>
            {s.image && <img src={s.image} alt="" loading="lazy" className="help-shot" />}
          </li>
        ))}
      </ol>
    </details>
  )
}
