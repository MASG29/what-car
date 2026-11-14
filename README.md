# car-finder

[English](#english) · [Português](#português)

## English

A personal used-car finder. It reads ads from Standvirtual and CustoJusto, and from Facebook
Marketplace and OLX through HAR files you save in your own browser (Marketplace can also be
searched automatically, see below). It drops the ads outside your limits (price, km, year, fuel,
distance from your centre, doors), scores the rest and shows the best five with the reasons
behind each score.

Each ad is scored on:

- **reliability**: inspection and breakdown surveys (TÜV, ADAC) for the exact model generation,
  plus known engine and gearbox faults by build date;
- **price**: compared with similar ads collected by the app;
- **consumption**: official type-approval figures (NEDC);
- **completeness and photos** of the ad.

It also estimates a "real cost" (asking price, paperwork, inspection, road tax and a timing belt
assumed due unless the ad says it was done) and builds a buying checklist for each car.

By default it searches the shortlist of models from the underlying research (Colt, Jazz, Yaris,
Mazda2, Fiesta, Fusion, Polo, Golf, Corolla, Auris, Civic, Mazda3). The "Todos os B/C" option
widens it to every B and C segment model in the knowledge base. Typing a make and model (for
example "Ford Fiesta", or just "Fiesta") narrows the results to that model and puts it in
the Standvirtual and CustoJusto search addresses and in the "Abrir pesquisa no Marketplace" link; a typo gets a "did you mean" suggestion. The UI is in European
Portuguese.

### Local use only

The app is meant to run on your own computer for your own search. The web server listens on
127.0.0.1 only, and Postgres is published on 127.0.0.1 only. Do not expose either to a network.

### Running it

Requirements: Java 21 or newer and Docker. The build downloads its own Node.js for the frontend.

```bash
./mvnw spring-boot:run
```

- Spring Boot starts Postgres from `compose.yaml` (host port **5433**) and stops it on exit.
- The packaged jar works the same way: `./mvnw package`, then `java -jar target/car-finder-*.jar`
  run from the repository folder (it needs `compose.yaml` there). To use your own Postgres
  instead, add `--spring.docker.compose.enabled=false` and the `--spring.datasource.*` properties.
- Open **http://127.0.0.1:8090**.
- The REST API is documented at http://127.0.0.1:8090/swagger-ui.html.
- Tests: `./mvnw verify`. Database tests use Testcontainers, so Docker must be running.
- Frontend development: `cd frontend && npm run dev`. Vite proxies `/api` to 127.0.0.1:8090.
- "Dados guardados" (bottom of the page) lists each HAR import and deletes one import (only the
  ads no other import holds), all saved searches, or all data. Your limits and weights stay.

### Sites

If a site refuses a request (HTTP 403 or 429, or 400 from Facebook), the app stops opening that
site's ad pages for the rest of the search at once and never retries; those ads keep their search
data.

- **Standvirtual**: the app reads public search and advert pages
  slowly, with a random pause of 4 to 9 seconds between requests and a page budget per run. It
  never requests `/api/`, `/ajax/`, `/account/` or `/ad/contact/`, it fetches only
  `www.standvirtual.com`, and it does not follow redirects.
- **Facebook Marketplace** (imported ads): the app never loads Marketplace searches, unless you
  turn on the automated search (below). You
  browse Marketplace in your own browser, save the network traffic as a HAR file, and upload it.
  The app reads the ads from it in memory and never writes the file to disk. It reads only the
  ad fields it needs, never the seller's name or id, headers or cookies. Steps (the same as in
  the app, with screenshots, under "Como gravar o ficheiro HAR"):
  1. "Abrir pesquisa no Marketplace" opens the vehicles search with your maximum price (Facebook
     ignores year and km in the link; the app filters them after the import).
  2. In Facebook's "Location" filter, set the same centre and radius as in the app.
  3. Press F12 and open the "Network" tab before reloading the page.
  4. Turn on the "Doc" and "Fetch/XHR" filters (Ctrl+click) and tick "Keep log" ("Preserve log"
     in some versions; "Persist Logs" in Firefox).
  5. Press F5 and scroll down: hold Page Down (or End). In Firefox you can use autoscroll instead:
     turn on "Settings > General > Browsing > Use autoscrolling", then middle-click and move the
     mouse down; click to stop.
  6. Save the HAR: the down-arrow "Export HAR" button in the Network toolbar, or right-click the
     list and "Save all as HAR".
  7. Click "Importar HAR" in the app and choose the file.

  **The HAR file holds your Facebook session: do not share it, and delete it after the import.**
  Each search that includes Marketplace ranks every imported ad, marked "importado há X dias",
  because a HAR cannot tell when an ad was removed; an ad seen sold or reserved in a later import
  is excluded.

  **Ad pages:** during a search, the app then opens the public page of every imported ad that
  passes your limits (best first, a random pause of 6 to 12 seconds between them, so 200 ads take
  about half an hour; a page read is not read again for 7 days) to read the
  description, which often gives the year, km, fuel, gearbox and condition. It does this logged
  out: no account, no cookies, plain HTTP with the headers a browser sends, only
  `https://www.facebook.com/marketplace/item/<id>/`, and it does not follow redirects. An ad that
  Facebook shows only after login keeps its imported data; after 3 login walls in a row the app
  stops opening ad pages for the rest of that search (no retries). Below the results, "Anúncios que
  não consegui abrir" lists those ads with their links so you can open them yourself. Facebook's terms forbid collecting
  data by automated means, so this part is an automated request that you run at your own risk
  (the usual one is an IP block).

  **Automated search (optional, off by default):** "Pesquisar também automaticamente (conta
  secundária)" makes a search open Marketplace itself in a visible Chromium window and scroll the
  results (up to 15 times, with pauses), reading the ads the page receives, as an import would.
  - Use a **secondary Facebook account only, never your main one.** Facebook's terms allow one
    personal account and forbid automated collection; the account (and possibly accounts linked
    to it) can be restricted. You run it at your own risk.
  - You log in yourself in that window; the app never types or stores your password. The session
    stays in the git-ignored `browser-profile/` folder.
  - Any verification screen (checkpoint, CAPTCHA, "confirm it's you") stops the run and closes
    the window; the app never tries to get past it.
  - The window only reads the search; ad pages are still opened logged out, as above.
- **CustoJusto** (off by default; ticking it warns that the site may block your IP if you search
  a lot): the app reads public car search pages for all of Portugal, newest first, with your
  maximum price and minimum year (and the typed make and model) in the address, up to 10 pages,
  then up to 40 advert pages per run (best first), with a random pause of 5 to 10 seconds between
  requests. The address also asks for a price of at least 100 €, so dealers' 0 € ads (which the
  app drops as symbolic prices) do not use up the page budget. Search pages give no km; the advert page gives only a band ("200 000 - 249 999"), so km
  are taken from the description only when it states one figure inside that band, otherwise they
  stay unknown. It fetches only `www.custojusto.pt` paths under `/<region>/veiculos/carros-usados`,
  sends no cookies, does not follow redirects, and marks an ad the site reports as gone as
  unavailable. CustoJusto's `robots.txt` (read on 2026-11-12) says: "It is expressly forbidden to
  use spiders or other automated methods to access CustoJusto.pt" without the site's permission,
  and it disallows result pages after the first (`o=`). Its rules page ("Regras", `/ajuda-rules`)
  forbids automated tools for posting ads and automated or mass messages; it says nothing about
  reading pages, which does not make reading them allowed. Using this source goes against the
  site's stated rule and is your own decision and risk.
- **OLX** (off by default; HAR import only): OLX answers any request that does not come from a
  browser with a block page (a CloudFront WAF "403 Request blocked"), so the app never requests
  anything from OLX and never tries to get past that block. You browse OLX's car search in your
  own browser, save a HAR file (the in-app guide shows how: F12, Network, Doc + Fetch/XHR,
  Preserve log, reload, 3 to 5 result pages, "Save all as HAR") and import it. The file is read
  in memory as it uploads and never written anywhere; only each ad's title, price, year,
  registration month, km, fuel, gearbox, engine size, power, doors, town and district, private or
  dealer, photo count, description and link are kept. The seller's name, id, contact, shop, map
  position, plate and VIN are never read. Ads OLX copies from Standvirtual are left out (the
  Standvirtual source reads them in full), and an ad the file shows as no longer active is marked
  unavailable. The HAR holds your OLX session cookies: do not share it, and delete it after
  importing. OLX's `robots.txt` and terms were not checked, because they cannot be fetched
  without a browser either (same 403).

Each site has its own terms of use. Standvirtual's terms, for example, ask for consent before
content is downloaded and forbid aggregating listings for third parties. This tool keeps results
on your own machine for your personal search and republishes nothing. **You are responsible for
checking and respecting each site's terms before running a search.**

### Location

You give a centre (a locality) and a radius in km; there are no defaults. Ads farther away in a
straight line are dropped with the distance as the reason. An ad whose place is ambiguous, unknown
or only known by district is kept with a note, never dropped for that alone. Typing a name shared by
several places asks for "Name, District". Places come from a trimmed file committed in
`src/main/resources/geo/places-pt.tsv` (rebuilt by `scripts/build-places.sh`); the app makes no
geo service calls.

Place data: [GeoNames](https://www.geonames.org/), licensed
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

### Knowledge data

The reliability, fault, gearbox and consumption tables live in `src/main/resources/knowledge/`.
Every row cites its source URL (survey editions, type-approval files, technical bulletins and
similar). Nothing is estimated: when no source was found, the row is missing and the UI shows
"sem dados". See `src/main/resources/knowledge/README.md` for how consumption figures were
chosen.

### Known limitations

- No login: any program on your computer can use the app while it runs. Web pages cannot: the
  app answers only requests addressed to `localhost:8090` or `127.0.0.1:8090` (which stops DNS
  rebinding), and the imports accept only `application/octet-stream`, which another site's page
  cannot send without the browser asking the app first.
- Only Standvirtual, Marketplace (through HAR import or the automated search), CustoJusto and OLX (through HAR import) are supported, and a change in their page
  format stops searches until the parser is updated (the app says so instead of returning empty
  results).
- The app does not look at photos. Damage that shows only in the pictures (dents, scratches, a
  repainted panel) is missed unless the title or description mentions it. Telling it from photos
  would need an image-recognition model, which this project does not use. When an ad costs 80% or
  less of the median of comparable ads, the app lists it in a separate "Suspeitos" tab, lowers its
  confidence, keeps it out of the top 5 and warns you to check its photos closely.
- The knowledge base covers B and C segment cars built up to about 2012. Other cars are dropped
  or shown without reliability data.

### Licence

MIT, see [LICENSE](LICENSE).

## Português

Um localizador pessoal de carros usados. Lê anúncios do Standvirtual e do CustoJusto, e do
Facebook Marketplace e do OLX através de ficheiros HAR que gravas no teu browser (o Marketplace
também pode ser pesquisado automaticamente, ver abaixo). Exclui os anúncios fora dos teus limites
(preço, km, ano, combustível, distância ao teu centro, portas), pontua os restantes e mostra os
cinco melhores com as razões de cada pontuação.

Cada anúncio é pontuado em:

- **fiabilidade**: estudos de inspeção e de avarias (TÜV, ADAC) para a geração exata do modelo,
  e avarias conhecidas de motor e caixa pela data de fabrico;
- **preço**: comparado com anúncios semelhantes recolhidos pela aplicação;
- **consumo**: valores oficiais de homologação (NEDC);
- **informação e fotos** do anúncio.

Também estima um "custo real" (preço pedido, papelada, inspeção, IUC e uma correia de
distribuição assumida por fazer, a menos que o anúncio diga que foi feita) e cria uma lista de
verificação para cada carro.

Por omissão, pesquisa a lista curta de modelos da investigação de base (Colt, Jazz, Yaris,
Mazda2, Fiesta, Fusion, Polo, Golf, Corolla, Auris, Civic, Mazda3). A opção "Todos os B/C"
alarga a pesquisa a todos os modelos dos segmentos B e C da base de conhecimento. Se escreveres
uma marca e modelo (por exemplo "Ford Fiesta", ou só "Fiesta"), os resultados ficam só com esse
modelo e ele vai nos endereços de pesquisa do Standvirtual e do CustoJusto e no link "Abrir pesquisa no Marketplace"; um erro de escrita dá a sugestão "Será
que querias dizer".

### Só para uso local

A aplicação foi feita para correr no teu computador, para a tua pesquisa. O servidor web só
escuta em 127.0.0.1 e o Postgres também só fica publicado em 127.0.0.1. Não exponhas nenhum dos
dois a uma rede.

### Como correr

Requisitos: Java 21 ou mais recente e Docker. A build descarrega o seu próprio Node.js para o
frontend.

```bash
./mvnw spring-boot:run
```

- O Spring Boot arranca o Postgres a partir do `compose.yaml` (porta **5433** no anfitrião) e
  para-o à saída.
- O jar empacotado funciona da mesma forma: `./mvnw package` e depois
  `java -jar target/car-finder-*.jar` na pasta do repositório (precisa do `compose.yaml` ali).
  Para usares o teu próprio Postgres, junta `--spring.docker.compose.enabled=false` e as
  propriedades `--spring.datasource.*`.
- Abre **http://127.0.0.1:8090**.
- A API REST está documentada em http://127.0.0.1:8090/swagger-ui.html.
- Testes: `./mvnw verify`. Os testes de base de dados usam Testcontainers, por isso o Docker tem
  de estar a correr.
- Desenvolvimento do frontend: `cd frontend && npm run dev`. O Vite encaminha `/api` para
  127.0.0.1:8090.
- "Dados guardados" (no fim da página) mostra cada importação de HAR e apaga uma importação (só
  os anúncios que nenhuma outra tem), todas as pesquisas guardadas ou todos os dados. Os teus
  limites e pesos ficam.

### Sites

Se um site recusar um pedido (HTTP 403 ou 429, ou 400 do Facebook), a aplicação deixa logo de abrir
páginas de anúncio desse site no resto da pesquisa e nunca tenta de novo; esses anúncios ficam com
os dados da pesquisa.

- **Standvirtual**: a aplicação lê páginas públicas de pesquisa e de
  anúncio devagar, com uma pausa aleatória de 4 a 9 segundos entre pedidos e um limite de páginas
  por pesquisa. Nunca pede `/api/`, `/ajax/`, `/account/` nem `/ad/contact/`, só acede a
  `www.standvirtual.com` e não segue redirecionamentos.
- **Facebook Marketplace** (anúncios importados): a aplicação nunca abre pesquisas do
  Marketplace, a não ser que ligues a pesquisa automática (abaixo). Vês o Marketplace no teu browser, gravas o tráfego de rede num ficheiro HAR e carregas esse ficheiro.
  A aplicação lê os anúncios em memória e nunca grava o ficheiro em disco. Lê só os campos do
  anúncio de que precisa, nunca o nome nem o id do vendedor, cabeçalhos ou cookies. Passos (os
  mesmos da aplicação, com capturas, em "Como gravar o ficheiro HAR"):
  1. "Abrir pesquisa no Marketplace" abre a pesquisa de veículos com o teu preço máximo (o
     Facebook ignora o ano e os km no link; a aplicação filtra-os depois de importares).
  2. No filtro "Localização" do Facebook, põe o mesmo centro e raio que puseste na aplicação.
  3. Carrega em F12 e abre o separador "Network" (Rede) antes de recarregar a página.
  4. Liga os filtros "Doc" e "Fetch/XHR" (Ctrl+clique) e marca "Keep log" ("Preserve log" nalgumas
     versões; "Persist Logs" no Firefox).
  5. Carrega em F5 e desce a página: mantém a tecla Page Down (ou End) carregada. No Firefox
     podes usar o deslocamento automático: liga "Definições > Geral > Navegação > Usar
     deslocamento automático", depois clica com o botão do meio e move o rato para baixo; clica
     outra vez para parar.
  6. Grava o HAR: o botão da seta para baixo ("Export HAR") na barra do Network, ou botão direito
     na lista e "Save all as HAR".
  7. Carrega em "Importar HAR" na aplicação e escolhe o ficheiro.

  **O ficheiro HAR tem a tua sessão do Facebook: não o partilhes e apaga-o depois de importar.**
  Cada pesquisa que inclui o Marketplace ordena todos os anúncios importados, com a indicação
  "importado há X dias", porque um HAR não diz quando um anúncio foi retirado; um anúncio visto
  como vendido ou reservado numa importação seguinte é excluído.

  **Páginas dos anúncios:** durante a pesquisa, a aplicação abre depois a página pública de
  cada anúncio importado que passa os teus limites (os melhores primeiro, com uma pausa aleatória
  de 6 a 12 segundos entre eles, por isso 200 anúncios demoram cerca de meia hora; uma página lida
  não volta a ser aberta durante 7 dias) para ler a descrição, que muitas vezes diz o ano, os km, o combustível, a
  caixa e o estado. Fá-lo sem sessão: sem conta, sem cookies, por HTTP simples com os cabeçalhos
  que um browser envia, só `https://www.facebook.com/marketplace/item/<id>/`, e não segue
  redirecionamentos. Um anúncio que o Facebook só mostra com login fica com os dados
  importados; depois de 3 pedidos de login seguidos, a aplicação deixa de abrir páginas de
  anúncios até ao fim dessa pesquisa (sem novas tentativas). Abaixo dos resultados, "Anúncios que
  não consegui abrir" mostra esses anúncios com o link, para os abrires tu. Os termos do Facebook proíbem recolher dados por meios automáticos; esta parte é
  um pedido automático que fazes por tua conta e risco (o mais comum é um bloqueio do IP).

  **Pesquisa automática (opcional, desligada por omissão):** "Pesquisar também automaticamente
  (conta secundária)" faz a pesquisa abrir o próprio Marketplace numa janela visível do Chromium e
  descer os resultados (até 15 vezes, com pausas), lendo os anúncios que a página recebe, como numa
  importação.
  - Usa **só uma conta secundária do Facebook, nunca a tua conta principal.** Os termos do
    Facebook permitem uma conta pessoal e proíbem a recolha automática; a conta (e talvez as
    contas ligadas a ela) pode ser restringida. Fazes isto por tua conta e risco.
  - És tu que fazes login nessa janela; a aplicação nunca escreve nem guarda a tua palavra-passe.
    A sessão fica na pasta `browser-profile/`, ignorada pelo git.
  - Qualquer ecrã de verificação (checkpoint, CAPTCHA, "confirma que és tu") para a pesquisa e
    fecha a janela; a aplicação nunca tenta passá-lo.
  - A janela só lê a pesquisa; as páginas dos anúncios continuam a ser abertas sem sessão, como
    acima.
- **CustoJusto** (desligado por omissão; ao marcá-lo, a aplicação avisa que o site pode bloquear o
  teu IP se pesquisares muito): a aplicação lê páginas públicas de pesquisa de carros de todo o
  país, as mais recentes primeiro, com o teu preço máximo e o ano mínimo (e a marca e modelo que
  escreveres) no endereço, até 10 páginas, e depois até 40 páginas de anúncio por pesquisa (os
  melhores primeiro), com uma pausa aleatória de 5 a 10 segundos entre pedidos. O endereço pede
  também um preço mínimo de 100 €, para que os anúncios de stands a 0 € (que a aplicação exclui
  como preço simbólico) não gastem as páginas. As páginas de pesquisa não dizem os km e a página do anúncio só dá um intervalo ("200 000 - 249 999"), por isso
  os km vêm da descrição só quando ela diz um único valor dentro desse intervalo; senão ficam
  desconhecidos. Só acede a caminhos de `www.custojusto.pt` em `/<região>/veiculos/carros-usados`,
  não envia cookies, não segue redirecionamentos e marca como indisponível um anúncio que o site
  diz ter sido retirado. O `robots.txt` do CustoJusto (lido a 2026-11-12) diz: "It is expressly
  forbidden to use spiders or other automated methods to access CustoJusto.pt" sem autorização do
  site, e bloqueia as páginas de resultados a seguir à primeira (`o=`). A página de regras
  ("Regras", `/ajuda-rules`) proíbe ferramentas automáticas para publicar anúncios e mensagens
  automáticas ou em massa; não diz nada sobre ler páginas, o que não quer dizer que seja
  permitido. Usar esta fonte vai contra a regra do site e é decisão e risco teus.
- **OLX** (desligado por omissão; só por importação de HAR): o OLX responde a qualquer pedido que
  não venha de um navegador com uma página de bloqueio (um "403 Request blocked" da firewall
  CloudFront), por isso a aplicação nunca pede nada ao OLX nem tenta contornar esse bloqueio.
  Navegas na pesquisa de carros do OLX no teu navegador, gravas um ficheiro HAR (o guia na
  aplicação explica: F12, Rede, Doc + Fetch/XHR, Manter registo, recarregar, 3 a 5 páginas de
  resultados, "Guardar tudo como HAR") e importas. O ficheiro é lido em memória enquanto é enviado
  e nunca é gravado; de cada anúncio só ficam o título, preço, ano, mês de registo, km,
  combustível, caixa, cilindrada, potência, portas, localidade e distrito, particular ou
  profissional, número de fotos, descrição e link. O nome, id, contacto, loja, posição no mapa,
  matrícula e VIN do vendedor nunca são lidos. Os anúncios que o OLX copia do Standvirtual ficam
  de fora (a fonte Standvirtual lê-os por inteiro), e um anúncio que o ficheiro mostra como já não
  ativo fica marcado como indisponível. O HAR tem os cookies da tua sessão do OLX: não o
  partilhes e apaga-o depois de importar. O `robots.txt` e os termos do OLX não foram verificados,
  porque também não se conseguem obter sem navegador (o mesmo 403).

Cada site tem os seus próprios termos de utilização. Os termos do Standvirtual, por exemplo,
pedem consentimento antes de se descarregar conteúdo e proíbem agregar anúncios para terceiros.
Esta ferramenta guarda os resultados no teu computador, para a tua pesquisa pessoal, e não
republica nada. **És responsável por verificar e respeitar os termos de cada site antes de
fazer uma pesquisa.**

### Localização

Indicas um centro (uma localidade) e um raio em km; não há valores por defeito. Os anúncios mais
longe em linha reta são excluídos, com a distância como razão. Um anúncio com localidade ambígua,
desconhecida ou só conhecida pelo distrito fica com uma nota e nunca é excluído só por isso. Se o
nome existir em várias localidades, escreve "Nome, Distrito". As localidades vêm de um ficheiro
reduzido, guardado em `src/main/resources/geo/places-pt.tsv` (gerado por `scripts/build-places.sh`);
a aplicação não chama nenhum serviço de geolocalização.

Dados de localidades: [GeoNames](https://www.geonames.org/), licença
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

### Dados de conhecimento

As tabelas de fiabilidade, avarias, caixas e consumos estão em `src/main/resources/knowledge/`.
Cada linha cita o URL da fonte (edições de estudos, ficheiros de homologação, boletins técnicos e
semelhantes). Nada é estimado: quando não se encontrou fonte, a linha não existe e a interface
mostra "sem dados". O `src/main/resources/knowledge/README.md` explica como foram escolhidos os
valores de consumo.

### Limitações conhecidas

- Não há login: qualquer programa no teu computador pode usar a aplicação enquanto ela corre. As
  páginas web não: a aplicação só responde a pedidos dirigidos a `localhost:8090` ou `127.0.0.1:8090`
  (o que trava o DNS rebinding), e as importações só aceitam `application/octet-stream`, que a
  página de outro site não consegue enviar sem o browser perguntar primeiro à aplicação.
- Só o Standvirtual, o Marketplace (por importação de HAR ou pela pesquisa automática), o CustoJusto e o OLX (por importação de HAR) são suportados, e uma mudança no
  formato das páginas para as pesquisas até o leitor ser atualizado (a aplicação avisa, em vez de
  devolver resultados vazios).
- A aplicação não olha para as fotos. Danos que só se veem nas imagens (amolgadelas, riscos, uma
  peça repintada) passam despercebidos se o título ou a descrição não os referirem. Detetá-los nas
  fotos exigiria um modelo de reconhecimento de imagem, que este projeto não usa. Quando um anúncio
  custa 80% ou menos da mediana dos anúncios comparáveis, a aplicação põe-no num separador à parte,
  "Suspeitos", baixa a confiança nele, deixa-o fora do Top 5 e avisa para veres as fotos com atenção.
- A base de conhecimento cobre carros dos segmentos B e C fabricados até cerca de 2012. Os outros são
  excluídos ou aparecem sem dados de fiabilidade.

### Licença

MIT, ver [LICENSE](LICENSE).
