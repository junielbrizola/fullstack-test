# Investment API

API local do desafio Coderockr, implementada com Java 25, Quarkus 3.40.1 e cliente reativo PostgreSQL. O contrato canônico é [`../../docs/api/openapi.yaml`](../../docs/api/openapi.yaml).

## Configuração local

O Docker Compose do repositório fornece PostgreSQL e a API como serviços separados. O serviço de migração Flyway deve aplicar `src/main/resources/db/migration` antes de iniciar a API. O banco precisa carregar `pg_stat_statements` em `shared_preload_libraries` para a segunda migração.

Variáveis aceitas pela API:

| Variável | Padrão local | Uso |
|---|---|---|
| `DATABASE_URL` | `postgresql://localhost:5432/investment_manager` | URL reativa PostgreSQL |
| `DB_USER` | `investment_app` | Papel de execução com privilégios mínimos |
| `DB_PASSWORD` | `local-dev-only` | Senha descartável apenas para execução local |
| `DB_POOL_MAX_SIZE` | `12` | Limite superior do pool reativo |
| `BUSINESS_TIME_ZONE` | `America/Sao_Paulo` | Fuso do relógio de negócio |
| `WEB_ORIGIN` | `http://localhost:3000` | Origem local permitida pelo CORS |

Os valores padrão não são credenciais de ambiente remoto. Para uso externo, forneça segredos pelo ambiente e não os grave no repositório.

## Rotas

- `POST /api/v1/investments` cria ou recupera investimento e exige `Idempotency-Key`.
- `GET /api/v1/investments/{investmentId}` consulta detalhe e saldo esperado.
- `GET /api/v1/investments?ownerName=&limit=20&cursor=` lista por cursor.
- `POST /api/v1/investments/{investmentId}/withdrawal-previews` calcula sem persistir.
- `POST /api/v1/investments/{investmentId}/withdrawals` confirma o resgate integral e exige `Idempotency-Key`.

Valores monetários trafegam como strings decimais. O principal inicial respeita `NUMERIC(19,2)`; saldos e valores calculados podem superar esse limite e mantêm precisão decimal. Respostas de erro usam `application/problem+json`. O Swagger UI fica habilitado apenas no perfil de desenvolvimento; o OpenAPI está em `/q/openapi`.

## Bibliotecas

| Biblioteca | Uso |
|---|---|
| Quarkus REST com Jackson | Endpoints REST reativos e JSON. |
| Quarkus Reactive PostgreSQL Client | Acesso assíncrono, transações e SQL preparado sem ORM no caminho de consulta/gravação. |
| Hibernate Validator | Validação dos DTOs HTTP. |
| SmallRye OpenAPI | Documentação HTTP servida pelo Quarkus. |
| SmallRye Health | Endpoints de saúde e prontidão usados pelo Compose. |
| Micrometer Prometheus Registry | Métricas locais em `/q/metrics`. |
| Flyway | Migrações versionadas, executadas pelo serviço local `migration`. |
| JUnit Jupiter e Quarkus JUnit 5 | Testes unitários de domínio e casos de uso; não executados nesta etapa estática. |

## Build local

O build de imagem compila o fast-jar em uma etapa Maven Java 25 e executa o artefato em uma imagem Java 25 JRE como usuário sem privilégios. Para subir o projeto completo, siga as instruções de variáveis locais em [`../../infra/README.md`](../../infra/README.md) e use o Compose na raiz do repositório.

## Estrutura

- `src/main/java/com/coderockr/investments/domain`: tipos e regras financeiras puras.
- `src/main/java/com/coderockr/investments/application`: casos de uso e portas.
- `src/main/java/com/coderockr/investments/adapter/in/rest`: recursos, DTOs e erros HTTP.
- `src/main/java/com/coderockr/investments/adapter/out/postgres`: SQL preparado e transações.
- `src/main/resources/db/migration`: migrações Flyway.
- `src/test/java`: testes unitários fonte; não foram executados nesta revisão code-only.

## Limites de comprovação

A entrega desta etapa foi revisada estaticamente. Build, testes, aplicação das migrações, inicialização de containers, integração e benchmark não foram executados.
