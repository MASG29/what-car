# car-finder

[English](#english) · [Português](#português)

## English

A personal used-car finder. It reads ads from Standvirtual, drops the ones outside your limits
(price, km, year, fuel, distance from your centre, doors), scores the rest and shows the best five with the reasons
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
widens it to every B and C segment model in the knowledge base. The UI is in European Portuguese.

### Local use only

The app is meant to run on your own computer for your own search. The web server listens on
127.0.0.1 only, and Postgres is published on 127.0.0.1 only. Do not expose either to a network.

### Running it

Requirements: Java 21 or newer and Docker. The build downloads its own Node.js for the frontend.

```bash
./mvnw spring-boot:run
```

- Spring Boot starts Postgres from `compose.yaml` (host port **5433**) and stops it on exit.
- Open **http://127.0.0.1:8090**.
- The REST API is documented at http://127.0.0.1:8090/swagger-ui.html.
- Tests: `./mvnw verify`. Database tests use Testcontainers, so Docker must be running.
- Frontend development: `cd frontend && npm run dev`. Vite proxies `/api` to 127.0.0.1:8090.

### Sites

- **Standvirtual** is the only active source. The app reads public search and advert pages
  slowly, with a random pause of 4 to 9 seconds between requests and a page budget per run. It
  never requests `/api/`, `/ajax/`, `/account/` or `/ad/contact/`, it fetches only
  `www.standvirtual.com`, and it does not follow redirects.
- **Facebook Marketplace** is paused. It appears in the UI as "Em pausa" and cannot be selected.

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

- No `Host` header check: a malicious web page could use DNS rebinding to reach the app on
  127.0.0.1 while it runs. The risk is low for a personal tool, but do not run it next to
  untrusted browsing if that matters to you.
- Only Standvirtual is supported, and a change in its page format stops searches until the
  parser is updated (the app says so instead of returning empty results).
- The knowledge base covers B and C segment cars built up to about 2012. Other cars are dropped
  or shown without reliability data.

### Licence

MIT, see [LICENSE](LICENSE).

## Português

Um localizador pessoal de carros usados. Lê anúncios do Standvirtual, exclui os que estão fora
dos teus limites (preço, km, ano, combustível, distância ao teu centro, portas), pontua os restantes e mostra os
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
alarga a pesquisa a todos os modelos dos segmentos B e C da base de conhecimento.

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
- Abre **http://127.0.0.1:8090**.
- A API REST está documentada em http://127.0.0.1:8090/swagger-ui.html.
- Testes: `./mvnw verify`. Os testes de base de dados usam Testcontainers, por isso o Docker tem
  de estar a correr.
- Desenvolvimento do frontend: `cd frontend && npm run dev`. O Vite encaminha `/api` para
  127.0.0.1:8090.

### Sites

- O **Standvirtual** é a única fonte ativa. A aplicação lê páginas públicas de pesquisa e de
  anúncio devagar, com uma pausa aleatória de 4 a 9 segundos entre pedidos e um limite de páginas
  por pesquisa. Nunca pede `/api/`, `/ajax/`, `/account/` nem `/ad/contact/`, só acede a
  `www.standvirtual.com` e não segue redirecionamentos.
- O **Facebook Marketplace** está em pausa. Aparece na interface como "Em pausa" e não pode ser
  escolhido.

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

- Não há verificação do cabeçalho `Host`: uma página maliciosa pode usar DNS rebinding para
  chegar à aplicação em 127.0.0.1 enquanto ela corre. O risco é baixo para uma ferramenta pessoal,
  mas não a deixes a correr junto de navegação pouco fiável se isso te preocupar.
- Só o Standvirtual é suportado, e uma mudança no formato das páginas para as pesquisas até o
  leitor ser atualizado (a aplicação avisa, em vez de devolver resultados vazios).
- A base de conhecimento cobre carros dos segmentos B e C fabricados até cerca de 2012. Os outros são
  excluídos ou aparecem sem dados de fiabilidade.

### Licença

MIT, ver [LICENSE](LICENSE).
