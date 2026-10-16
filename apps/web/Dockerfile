FROM node:24.21.0-alpine3.24 AS build

WORKDIR /workspace/apps/web

# Required during SvelteKit route analysis; the runtime container receives its own values from Compose.
ENV API_INTERNAL_BASE_URL=http://api:8080 \
    API_PUBLIC_BASE_URL=http://localhost:8080 \
    PUBLIC_BUSINESS_TIME_ZONE=America/Sao_Paulo

COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY apps/web/ ./
COPY docs/api/openapi.yaml /workspace/docs/api/openapi.yaml
RUN npm run build \
    && npm prune --omit=dev --no-audit --no-fund

FROM node:24.21.0-alpine3.24 AS runtime

ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=3000

WORKDIR /app

COPY --from=build --chown=node:node /workspace/apps/web/package.json ./package.json
COPY --from=build --chown=node:node /workspace/apps/web/node_modules ./node_modules
COPY --from=build --chown=node:node /workspace/apps/web/build ./build

USER node

EXPOSE 3000

CMD ["node", "build"]
