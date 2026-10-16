# Web — Carteira de investimentos

Aplicação web SSR em Svelte 5, SvelteKit 3 e TypeScript estrito. O serviço roda em Node com `adapter-node`, consulta a API pelo contrato OpenAPI e não calcula juros, impostos, períodos ou saldos.

## Jornadas

- `/investments`: lista paginada por cursor, filtros inclusivos por data de criação, filtro por correspondência exata do titular e estados vazio/erro.
- `/investments/new`: criação com validação de titular, data e valor decimal; a chave idempotente e os dados incertos ficam guardados na sessão para retry seguro.
- `/investments/[investmentId]`: detalhe, posição e recibo de resgate.
- `/investments/[investmentId]/withdraw`: prévia, confirmação integral com chave de idempotência e reconciliação do estado após resposta incerta.

Valores financeiros permanecem strings decimais até a formatação visual. A apresentação agrupa a parte inteira com `BigInt` e mantém os centavos como texto, sem conversão monetária para `number`.

## Execução local

O Dockerfile expõe o Node SSR em `0.0.0.0:3000`. No Compose, configure:

| Variável | Valor local no Compose | Uso |
|---|---|---|
| `API_INTERNAL_BASE_URL` | `http://api:8080` | Chamadas SSR do serviço web à API |
| `API_PUBLIC_BASE_URL` | `http://localhost:8080` | Chamadas do navegador à API |
| `PUBLIC_BUSINESS_TIME_ZONE` | `America/Sao_Paulo` | Data atual exibida e limites de data do formulário; ao executar os serviços separadamente, deve ser igual a `BUSINESS_TIME_ZONE` da API |

As URLs são bases sem `/api/v1`; o cliente acrescenta esse prefixo. A API deve liberar por CORS a origem web usada pelo navegador. Para rodar o servidor de desenvolvimento fora do Docker, copie `.env.example` para `.env` e ajuste `API_INTERNAL_BASE_URL` para `http://localhost:8080`.

O build de imagem lê `docs/api/openapi.yaml`, regenera os tipos consumidos pela interface e executa a análise estática antes do bundle. O `package-lock.json` fixa as dependências transitivas e o Docker usa `npm ci`.

## Bibliotecas

- `svelte` e `@sveltejs/kit`: componentes reativos, roteamento e renderização SSR.
- `@sveltejs/adapter-node`: saída Node independente para o serviço web.
- `typescript` e `svelte-check`: tipagem estrita e verificação estática do Svelte.
- `vite` e `@sveltejs/vite-plugin-svelte`: compilação e integração de desenvolvimento.
- `@hey-api/openapi-ts`: geração apenas de tipos TypeScript a partir do contrato OpenAPI (`npm run api:types`); o plugin de tipos não adiciona código ao bundle em runtime.

O CSS usa tokens próprios e fontes instaladas no sistema; a interface não carrega fontes, imagens ou scripts de terceiros pela rede.

## Contrato e análise estática

Contrato da API: [`../../docs/api/openapi.yaml`](../../docs/api/openapi.yaml).

Comandos disponíveis para análise estática e build:

```sh
npm run check
npm run build
```

`npm run check` gera os tipos a partir do OpenAPI antes de executar SvelteKit Sync e `svelte-check`. O build do Compose executa `npm run build`, que aplica essa análise antes do bundle. Capturas de tela dependem da execução visual e permanecem pendentes.
