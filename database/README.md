# PostgreSQL

## Contrato persistido

O schema canônico é mantido pelas migrações Flyway em `apps/api/src/main/resources/db/migration`; este diretório não contém cópias das migrations. A V1 persiste `investment` e `withdrawal`, usa UUID e limita o principal a `NUMERIC(19,2)`. A V3 instala checks `isfinite` como `NOT VALID`, protegendo novas gravações sem varrer as tabelas; a V4 valida o conteúdo existente em migration separada. A API representa o intervalo finito PostgreSQL com o formato ISO local expandido do Java. Os valores calculados usam `NUMERIC` sem typmod, pois saldo e ganhos válidos podem superar o principal máximo; checks preservam escala de centavos, não negatividade e as equações do resgate. A tabela limita um resgate por investimento; cada tabela mantém sua chave idempotente única e limitada a 100 caracteres. A criação persiste sua chave para recuperar a solicitação após resposta perdida; o caso de uso compara o titular sem espaços externos e o principal normalizado a centavos.

A V2 instala `pg_stat_statements`. O Compose carrega a biblioteca na inicialização do PostgreSQL e a execução Flyway aplica `CREATE EXTENSION` no banco do projeto. Consultas de negócio não dependem da view de estatísticas.

## Índices e integridade

Os índices `(owner_name, created_on DESC, id DESC)` e `(created_on DESC, id DESC)` atendem respectivamente às listagens filtradas e sem filtro, ambas com cursor por data/id. O adapter compõe o texto SQL a partir de predicados fixos e posicionais para titular, datas inclusivas e cursor; os valores continuam vinculados como parâmetros preparados, nunca interpolados. Os filtros de data e titular precedem o predicado do cursor e o limite, preservando a ordenação `(created_on DESC, id DESC)`. As chaves primárias/únicas atendem à busca por UUID, à unicidade do resgate, às chaves idempotentes de criação e resgate.

Checks e trigger de V1 protegem valores não negativos, centavos, aritmética do resgate, igualdade do principal com o investimento, `withdrawn_on >= created_on` e as três faixas de alíquota. A V3 impede novas datas `infinity` e `-infinity`; a V4 valida os dados anteriores. O schema não impede datas futuras, pois os endpoints validam a data corrente com o mesmo `BUSINESS_TIME_ZONE` enviado pela configuração Compose à API e à interface. A composição mensal exata permanece responsabilidade da aplicação Java e sua avaliação é enviada ao pool de workers. Resgates são transacionais, bloqueiam a linha do investimento e repetem a consulta da chave idempotente após adquirir o lock para devolver o resultado persistido em retries concorrentes. O papel da API recebe `lock_timeout` de 3 segundos e `statement_timeout` de 10 segundos para novas sessões.

Ao atualizar um volume local existente, V3 instala os checks sem validar as linhas históricas; as restrições passam a proteger novas gravações. V4 valida os dados existentes e falha se houver datas infinitas. Nesse caso, inspecione `SELECT id, created_on FROM investment WHERE NOT isfinite(created_on)` e `SELECT id, withdrawn_on FROM withdrawal WHERE NOT isfinite(withdrawn_on)`, decida a correção de cada registro e recupere a migration V4 conforme o estado do histórico Flyway antes de reaplicá-la. A sequência não converte nem apaga dados automaticamente. A validação ocorre em migration separada para permitir que gravações normais prossigam enquanto V4 examina as linhas existentes.

## Extensões

| Extensão | Decisão | Motivo e limite |
|---|---|---|
| `pg_stat_statements` | Ativa | Mede estatísticas agregadas de SQL para análise local de consultas. Requer `shared_preload_libraries` e `CREATE EXTENSION`; não acelera automaticamente as consultas. |
| `pg_trgm` | Condicional | Só acrescentar se uma jornada aprovar busca textual por trecho/tolerante a erros em nome do titular, com consulta e índice GIN/GiST correspondentes. |
| `unaccent` | Condicional | Só acrescentar se a busca precisar tratar nomes com e sem acentos como equivalentes e o contrato definir essa semântica. |
| `citext` | Condicional | Não há e-mail/login no modelo atual. Reavaliar se identidade textual insensível a maiúsculas for introduzida. |
| `pg_buffercache`, `pgstattuple` | Diagnóstico opcional | Úteis em investigação operacional controlada; não fazem parte do caminho de negócio nem do bootstrap padrão. |

Extensões adicionais não são instaladas por catálogo. `uuid-ossp` é desnecessária para UUIDs gerados pela aplicação/PostgreSQL moderno; `pgcrypto` não substitui gestão de chaves nem criptografia de volume; PostGIS, TimescaleDB, `hstore`, `btree_gist`, `tablefunc`, `ltree` e extensões de fila/geoespacial não têm jornada correspondente neste desafio. Cada extensão adiciona superfície de atualização, permissões, compatibilidade e custo operacional; habilitar extensões sem uma consulta ou regra concreta não é otimização.

## Operação e otimização

O Compose usa PostgreSQL `18.6`, volume persistente e `pg_stat_statements` carregado desde a inicialização. Um passo pós-migration concede `SELECT`/`INSERT` nas tabelas de negócio e `UPDATE` somente em `investment.created_at`, privilégio exigido pelo PostgreSQL para `SELECT ... FOR UPDATE`; as consultas da aplicação não atualizam essa coluna. O histórico Flyway fica fora do alcance do papel da API. Não desative `fsync` ou a durabilidade WAL para perseguir velocidade. `NUMERIC` mantém correção monetária para principal e resultados calculados; otimização posterior deve partir de consultas e medições representativas, sem trocar precisão por ponto flutuante.

Links oficiais: [numeric types](https://www.postgresql.org/docs/18/datatype-numeric.html), [SELECT row-lock privileges](https://www.postgresql.org/docs/18/sql-select.html), [pg_stat_statements](https://www.postgresql.org/docs/18/pgstatstatements.html), [CREATE EXTENSION](https://www.postgresql.org/docs/18/sql-createextension.html), [multicolumn indexes](https://www.postgresql.org/docs/18/indexes-multicolumn.html), [WAL reliability](https://www.postgresql.org/docs/18/wal-reliability.html).

### Medir a listagem local

O benchmark `benchmark-list-api.mjs` usa somente `fetch` do Node e faz apenas `GET` na API local. Ele exige o nome de um titular sintético, confirma que a consulta encontra dados, limita a página a 100 itens e aceita até oito níveis de concorrência. Por padrão, aquece cada nível por 5 segundos e mede por 10 segundos; emite total de requisições, erros, vazão de respostas bem-sucedidas e p50/p95/p99/máximo da latência. Armazena no máximo 100 mil amostras de latência por nível, usando amostragem reservoir uniforme em execuções maiores para manter o consumo de memória previsível; o máximo é acompanhado em todas as requisições. O processo do benchmark não grava nem altera dados.

Para reproduzir uma carga sintética local com 10 mil linhas, distribuídas entre 10 titulares com 1.000 investimentos cada, execute o fixture idempotente abaixo. Ele insere somente registros com titular e chaves reservados para benchmark; esse passo é separado do processo GET-only.

```sh
docker compose exec -T db psql --no-psqlrc -v ON_ERROR_STOP=1 \
  --username investment_admin --dbname investment_manager \
  < database/seed-benchmark.sql
```

Esse fixture usa titulares, chaves e valores reservados diferentes do conjunto E2E usado na comparação histórica a seguir, mas mantém o mesmo formato de carga: 10 mil linhas em 10 titulares e 1.000 linhas para o filtro individual. Em uma instalação limpa, execute o benchmark abaixo com `BENCHMARK-LOCAL-G01` para fazer um novo A/B de pool com essa cardinalidade. O fixture não recria o conjunto nem os resultados históricos; para repetir aquela rodada, carregue o conjunto E2E original e use o filtro `E2E-LOAD-20261016-G01`. Os resultados variam com os dados e os recursos da máquina.

```sh
node database/benchmark-list-api.mjs \
  --owner-name 'BENCHMARK-LOCAL-G01' \
  --limit 100 \
  --concurrency 1,8,16,32 \
  --warmup-seconds 8 \
  --duration-seconds 10
```

Use uma carga de dados sintética estável e mantenha o mesmo titular, limite de página, estado de aquecimento e níveis de concorrência ao comparar execuções. O alvo aceita apenas `localhost`, `127.0.0.1` ou `::1` e não segue redirecionamentos; para registrar a saída em JSON, acrescente `--json`. O JSON inclui limite, níveis de concorrência, aquecimento, janela de carga, timeout configurado, drenagem final e o SHA-256 do filtro de titular, sem gravar o nome bruto. Cada janela encerra o início de novas requisições no prazo e limita o timeout efetivo em voo à duração da janela; a vazão usa o tempo real até terminar a drenagem. Resultados de uma execução local em cache aquecido medem o caminho API/HTTP/cliente junto com a consulta e não equivalem a capacidade de produção, cache frio ou disco sob falha. Não ajuste memória, WAL ou pool a partir de uma execução isolada; compare várias rodadas e também observe `pg_stat_statements`, planos com `EXPLAIN (ANALYZE, BUFFERS)` e recursos do host.

#### Comparação local de pool (16 de outubro de 2026)

O banco local tinha 10.002 investimentos; a carga E2E continha 10 mil linhas distribuídas entre 10 titulares. A comparação abaixo filtrou `E2E-LOAD-20261016-G01`, com 1.000 linhas, usou página 100, concorrências 8/16/32, aquecimento de 3 segundos e janela de carga de 4 segundos por nível. O mesmo benchmark GET-only foi executado uma vez com `DB_POOL_MAX_SIZE=12` e outra com `DB_POOL_MAX_SIZE=24`; entre as rodadas, somente esse valor foi alterado em `infra/local/api.env` e a API foi recriada. Ambos terminaram com zero erros e sem drenagem após a janela.

Comando usado nas duas rodadas:

```sh
node database/benchmark-list-api.mjs --owner-name 'E2E-LOAD-20261016-G01' \
  --limit 100 --concurrency 8,16,32 --warmup-seconds 3 \
  --duration-seconds 4 --timeout-seconds 10 --json
```

Para fazer um novo A/B com o fixture acima, troque o filtro do comando para `BENCHMARK-LOCAL-G01`, altere somente `DB_POOL_MAX_SIZE` no arquivo local de ambiente, recrie a API e execute o benchmark; repita com 12 e 24 e restaure 12 ao final. Para repetir a rodada E2E histórica, o conjunto E2E original precisa estar carregado; use o filtro `E2E-LOAD-20261016-G01` do comando registrado:

```sh
sed -i 's/^DB_POOL_MAX_SIZE=.*/DB_POOL_MAX_SIZE=12/' infra/local/api.env
docker compose up -d --no-deps --force-recreate api
# executar o benchmark
sed -i 's/^DB_POOL_MAX_SIZE=.*/DB_POOL_MAX_SIZE=24/' infra/local/api.env
docker compose up -d --no-deps --force-recreate api
# executar o mesmo benchmark
sed -i 's/^DB_POOL_MAX_SIZE=.*/DB_POOL_MAX_SIZE=12/' infra/local/api.env
docker compose up -d --no-deps --force-recreate api
```

| Pool | Concorrência | Requisições | Erros | Respostas/s | p95 (ms) | Máximo (ms) |
|---:|---:|---:|---:|---:|---:|---:|
| 12 | 8 | 29.409 | 0 | 7.351,22 | 2,03 | 6,63 |
| 12 | 16 | 32.354 | 0 | 8.086,42 | 3,79 | 7,75 |
| 12 | 32 | 29.645 | 0 | 7.406,68 | 7,46 | 16,40 |
| 24 | 8 | 26.099 | 0 | 6.523,78 | 2,35 | 5,03 |
| 24 | 16 | 30.301 | 0 | 7.572,98 | 4,00 | 7,91 |
| 24 | 32 | 27.575 | 0 | 6.890,01 | 7,81 | 12,93 |

Nesta rodada o pool 12 superou o pool 24 em vazão nos três níveis, com p95 ligeiramente menor. Uma comparação local curta não define capacidade de produção, mas não sustenta elevar o pool; a configuração foi restaurada para 12 e a API voltou a ficar saudável.

Nos planos quentes da consulta paginada, as variantes filtrada por titular/data e sem filtro usaram os índices existentes, leram 204 buffers em cache e não fizeram leituras físicas; os tempos medidos foram aproximadamente 0,15 ms e 0,20 ms. No `pg_stat_statements` local, a consulta com junção teve média próxima de 0,20 ms. Esses números são observações desta máquina e deste conjunto, não metas de SLA. Hoje não há evidência de que índices adicionais, extensões de busca ou parâmetros de memória diferentes acelerem o caminho medido; reavalie-os quando a jornada, o volume ou a distribuição de dados mudar.
