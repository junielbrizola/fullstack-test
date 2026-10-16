# PostgreSQL

## Contrato persistido

O schema canônico é mantido pelas migrações Flyway em `apps/api/src/main/resources/db/migration`; este diretório não contém cópias das migrations. A V1 persiste `investment` e `withdrawal`, usa UUID e limita o principal a `NUMERIC(19,2)`. Os valores calculados usam `NUMERIC` sem typmod, pois saldo e ganhos válidos podem superar o principal máximo; checks preservam escala de centavos, não negatividade e as equações do resgate. A tabela limita um resgate por investimento; cada tabela mantém sua chave idempotente única e limitada a 100 caracteres. A criação persiste sua chave para recuperar a solicitação após resposta perdida; o caso de uso compara o titular sem espaços externos e o principal normalizado a centavos.

A V2 instala `pg_stat_statements`. O Compose carrega a biblioteca na inicialização do PostgreSQL e a execução Flyway aplica `CREATE EXTENSION` no banco do projeto. Consultas de negócio não dependem da view de estatísticas.

## Índices e integridade

Os índices `(owner_name, created_on DESC, id DESC)` e `(created_on DESC, id DESC)` atendem respectivamente às listagens filtradas e sem filtro, ambas com cursor por data/id. O adapter seleciona SQL preparado separado por filtro e presença do cursor para que cada caminho mantenha predicados e ordenação compatíveis com o índice. As chaves primárias/únicas atendem à busca por UUID, à unicidade do resgate, às chaves idempotentes de criação e resgate.

Checks e trigger de V1 protegem valores não negativos, centavos, aritmética do resgate, igualdade do principal com o investimento, `withdrawn_on >= created_on` e as três faixas de alíquota. O schema não impede datas futuras: os endpoints de criação e resgate validam a data corrente usando `Clock` em `America/Sao_Paulo`; escrita SQL privilegiada fica fora do contrato da API. A composição mensal exata também permanece responsabilidade da aplicação Java e sua avaliação é enviada ao pool de workers. Resgates são transacionais, bloqueiam a linha do investimento e repetem a consulta da chave idempotente após adquirir o lock para devolver o resultado persistido em retries concorrentes. O papel da API recebe `lock_timeout` de 3 segundos e `statement_timeout` de 10 segundos para novas sessões.

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
