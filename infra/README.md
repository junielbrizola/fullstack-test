# Ambiente local

O Compose da raiz sobe PostgreSQL, uma execução Flyway, um passo curto de permissões, API e interface web. As portas publicadas ficam limitadas a `127.0.0.1`; o volume nomeado `postgres_data` mantém os dados do PostgreSQL. Não há credenciais utilizáveis versionadas.

## Preparação

1. Copie `infra/local/postgres.env.example` para `infra/local/postgres.env` e preencha `POSTGRES_PASSWORD` e `APP_DB_PASSWORD` com valores locais diferentes.
2. Copie `infra/local/migration.env.example` para `infra/local/migration.env` e defina `FLYWAY_PASSWORD` com o mesmo valor de `POSTGRES_PASSWORD`.
3. Copie `infra/local/api.env.example` para `infra/local/api.env` e defina `DB_PASSWORD` com o mesmo valor de `APP_DB_PASSWORD`.
4. Execute `docker compose up --build` na raiz do repositório.

Opcionalmente, copie `.env.example` para `.env` na raiz e defina `BUSINESS_TIME_ZONE`. O Compose envia o mesmo fuso à API e à interface; o padrão é `America/Sao_Paulo`.

Os arquivos `.env` gerados são ignorados por `infra/.gitignore`. Se o bootstrap do primeiro volume falhar por configuração inválida, corrija os valores e recrie apenas o ambiente local depois de preservar os dados que quiser manter.

## Papéis e migrações

O bootstrap cria `investment_app`, que recebe `CONNECT` e `USAGE` no schema `public`. Após Flyway concluir, o passo `permissions` concede `SELECT`/`INSERT` nas tabelas `investment` e `withdrawal`, mais `UPDATE` apenas na coluna `investment.created_at`. PostgreSQL exige `UPDATE` em ao menos uma coluna para `SELECT ... FOR UPDATE`; o adapter usa isso para serializar resgates e não atualiza `created_at`. O papel também recebe limites de sessão para novas conexões: `lock_timeout` de 3 segundos e `statement_timeout` de 10 segundos. O papel da API não recebe privilégios sobre o histórico Flyway. O serviço Flyway é de execução única e espera o banco ficar saudável; a API só inicia após migrações e concessão de privilégios concluírem.

Para este ambiente local, Flyway usa o papel administrativo inicial do contêiner porque a migração existente `V2__enable_pg_stat_statements.sql` instala uma extensão que requer privilégio administrativo. A senha administrativa fica restrita ao contêiner PostgreSQL e ao serviço de migração, não é passada à API. As migrações canônicas ficam em `apps/api/src/main/resources/db/migration`; este diretório não replica o schema.

O volume é persistente. `docker compose down` preserva os dados; remova o volume somente quando quiser apagar explicitamente o banco local. A senha do administrador, a autenticação inicial e os scripts em `infra/postgres/init` só são aplicados quando um diretório de dados vazio é inicializado; editar os arquivos de ambiente depois não troca senhas já gravadas no volume.
