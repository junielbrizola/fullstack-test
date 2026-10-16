# Coderockr Investment Management

Implementação do [desafio Fullstack da Coderockr](https://github.com/Coderockr/fullstack-test), com uma interface web para criar, consultar e resgatar investimentos. O Figma fornecido serve como referência visual para as jornadas de lista, detalhe, criação e resgate.

Referências visuais do desafio: [design no Figma](https://www.figma.com/design/jkilpjx9Q6hZnnCBHZcAWG/Coderockr-Fullstack---Test?node-id=0-1&p=f) e [protótipo](https://www.figma.com/proto/jkilpjx9Q6hZnnCBHZcAWG/Coderockr-Fullstack---Test?node-id=1-2&p=f&t=thQWSMxSHxCJTtAs-0&scaling=scale-down-width&content-scaling=fixed&page-id=0%3A1). Os quadros de lista, criação e resgate foram inspecionados e orientam a estrutura e a hierarquia visual da interface.

## Stack

- **Web:** Svelte 5, SvelteKit 3, TypeScript estrito e `adapter-node`, em serviço Docker independente.
- **API:** Java 25, Quarkus 3.40.1, Quarkus REST, Mutiny e cliente PostgreSQL reativo. A aplicação usa JVM fast-jar e SQL preparado.
- **Dados:** PostgreSQL 18.6 e migrações Flyway versionadas.
- **Ambiente local:** Docker Compose com serviços web, API, banco, migração e aplicação de privilégios. As portas ficam em `127.0.0.1`.

SvelteKit mantém interface e API web em processos separados, com SSR em Node. Quarkus foi escolhido para API Java com I/O PostgreSQL assíncrono e consultas explícitas. PostgreSQL mantém integridade relacional, datas civis e valores exatos; `pg_stat_statements` fornece estatísticas locais para orientar otimização posterior. As escolhas priorizam uma base enxuta e mensurável, sem alegação de benchmark ou de que uma tecnologia seja universalmente a mais rápida.

A arquitetura evita ponto flutuante para dinheiro: a API usa strings decimais, os valores de domínio usam `BigDecimal` e o acumulador mensal trabalha em centavos inteiros com `BigInteger`. O principal usa `NUMERIC(19,2)`; resultados calculados usam `NUMERIC` sem limite artificial de precisão. Os períodos contam aniversários mensais completos, com juros compostos de 0,52% arredondados a centavos por mês. Criação e resgate têm chaves idempotentes para repetir chamadas ambíguas sem duplicar operações. O código e o modelo não alegam desempenho medido.

## Requisitos locais

- Docker Engine com Docker Compose v2.
- A porta `3000` para a interface e `8080` para a API; a porta `5432` é publicada somente em loopback para ferramentas locais.

## Preparar e iniciar

Crie os arquivos locais de configuração. Use a mesma senha administrativa para `POSTGRES_PASSWORD` e `FLYWAY_PASSWORD`, e a mesma senha restrita da API para `APP_DB_PASSWORD` e `DB_PASSWORD`; as duas senhas devem ser diferentes:

```sh
cp infra/local/postgres.env.example infra/local/postgres.env
cp infra/local/migration.env.example infra/local/migration.env
cp infra/local/api.env.example infra/local/api.env
```

Configure `POSTGRES_PASSWORD` e `APP_DB_PASSWORD` em `infra/local/postgres.env`, `FLYWAY_PASSWORD` em `infra/local/migration.env` e `DB_PASSWORD` em `infra/local/api.env`, mantendo os valores correspondentes conforme os comentários nos exemplos. Esses arquivos são ignorados pelo Git.

Opcionalmente, copie `.env.example` para `.env` na raiz do Compose e defina `BUSINESS_TIME_ZONE`. O mesmo valor configura o relógio da API e os limites de data da interface; o padrão é `America/Sao_Paulo`.

Inicie os serviços na raiz:

```sh
docker compose up --build
```

- Interface: <http://localhost:3000>
- API: <http://localhost:8080/api/v1/investments>
- Especificação OpenAPI: [`docs/api/openapi.yaml`](docs/api/openapi.yaml)

O serviço Flyway espera o banco ficar saudável. A API só inicia depois da migração e da concessão de `SELECT`/`INSERT` nas tabelas de negócio e `UPDATE` somente na coluna `investment.created_at`, necessário para o lock transacional `SELECT ... FOR UPDATE`. O adapter não atualiza essa coluna. `docker compose down` mantém o volume `postgres_data`; remover dados locais exige remover explicitamente esse volume.

## Funcionalidades

- Lista paginada, filtros inclusivos de data de criação, filtro por correspondência exata do titular, saldo esperado e estado do investimento.
- Detalhe com principal, ganhos compostos, data de referência e histórico do resgate.
- Criação com titular, data de hoje ou passada no formato ISO local, dentro do intervalo finito de `DATE` do PostgreSQL, e valor não negativo.
- Prévia e confirmação de resgate integral em data permitida, com bruto, ganho, alíquota, imposto e líquido.
- Estados de carregamento, vazio, erro, validação, conflito e resultado incerto; a interface persiste a chave e a data de resgate pendentes na sessão e reconcilia o resultado antes de permitir o replay.

## Documentação

- Contrato HTTP: [`docs/api/openapi.yaml`](docs/api/openapi.yaml).
- Configuração local e papéis do banco: [`infra/README.md`](infra/README.md) e [`database/README.md`](database/README.md).
- Backend e suas dependências: [`apps/api/README.md`](apps/api/README.md).
- Web e suas dependências: [`apps/web/README.md`](apps/web/README.md).

As dependências diretas e transitivas do frontend estão fixadas em `apps/web/package.json` e `apps/web/package-lock.json`. O build da imagem web executa a análise Svelte/TypeScript e a compilação de produção. Os testes da API estão em `apps/api/src/test`.

## Bibliotecas de terceiros

### Web

- **Svelte / SvelteKit:** componentes reativos, rotas, renderização no servidor e integração com Vite.
- **`@sveltejs/adapter-node`:** servidor Node independente para o contêiner web.
- **TypeScript:** tipos estritos para rotas, formulário e respostas da API.
- **`@hey-api/openapi-ts`:** geração apenas de tipos TypeScript do contrato OpenAPI via script `api:types`.
- **`svelte-check`:** análise estática de Svelte e TypeScript via script `check`.

A interface usa CSS próprio, elementos semânticos e fontes do sistema; não carrega analytics, fontes ou componentes de terceiros em runtime.

### API

- **Quarkus REST / Jackson:** endpoints HTTP e serialização JSON.
- **Mutiny / Reactive PostgreSQL Client:** operações assíncronas e consultas PostgreSQL preparadas.
- **Hibernate Validator:** validação declarativa dos corpos HTTP.
- **SmallRye OpenAPI / Health:** especificação e health checks da API.
- **Micrometer Prometheus Registry:** métricas locais da API no endpoint `/q/metrics`.
- **JUnit 5 / Quarkus JUnit 5:** testes unitários do domínio e dos casos de uso.

### Banco

- **Flyway:** histórico versionado de migrations SQL.
- **`pg_stat_statements`:** estatísticas agregadas de consultas; requer preload na inicialização e extensão habilitada no banco.
- O catálogo de extensões é avaliado em [`database/README.md`](database/README.md). Extensões sem consulta ou regra concreta não são instaladas como tentativa de otimização.

## Limites conhecidos

- O projeto não implementa autenticação. `ownerName` é um filtro/identificador de demonstração e não constitui autorização ou isolamento de dados.
- O desafio pede ao menos duas screenshots reais em `screenshots/`; essa entrega ainda está pendente.
- O arredondamento mensal exato percorre cada período completo; o trabalho é agendado no pool de workers para não ocupar o event loop. O contrato aceita datas finitas de `DATE` do PostgreSQL; a composição desde a data mínima até hoje pode percorrer cerca de 80 mil períodos por investimento, e uma página aceita até 100 investimentos. O custo precisa de medição runtime.
- A criação precisa de um `Idempotency-Key` único por chamada lógica. O navegador mantém a chave e os dados pendentes na sessão até receber uma resposta definitiva.
- Testes de concorrência contra PostgreSQL continuam pendentes. O build local mais recente e os health checks dos serviços passaram; a validação visual por navegador e as screenshots ainda precisam ser concluídas.
- O `database/README.md` registra uma comparação local do pool de conexões; uma avaliação ampla de desempenho continua pendente e esses números não representam capacidade de produção.

## Entrega do desafio

- O desenvolvimento deve permanecer na branch `development`; não enviar alterações diretamente para `main`.
- Usar Conventional Commits durante a implementação.
- Depois da validação local e da inclusão de pelo menos duas capturas reais em `screenshots/`, abrir um pull request de `development` para `main`.
