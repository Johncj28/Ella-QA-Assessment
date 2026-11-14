# syntax=docker/dockerfile:1
#
# Multi-stage build for the Ella API.
#   deps      -> full dependency tree (needed to compile)
#   build     -> compiles TypeScript to dist/
#   prod-deps -> production-only node_modules (no compilers, linters, test tools)
#   runtime   -> minimal image: dist + prod node_modules, non-root, healthcheck
#
# Configuration is NEVER baked into the image: DB credentials etc. are injected at
# runtime (docker compose env_file / orchestrator secrets). The same image is promoted
# unchanged from test -> staging -> production.

ARG NODE_VERSION=20

FROM node:${NODE_VERSION}-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
# `npm ci` = exact lockfile install, fails if package.json and lockfile disagree.
RUN npm ci --no-audit --no-fund

FROM deps AS build
COPY tsconfig.json tsconfig.build.json nest-cli.json ./
COPY src ./src
RUN npm run build

FROM node:${NODE_VERSION}-alpine AS prod-deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund && npm cache clean --force

FROM node:${NODE_VERSION}-alpine AS runtime
ARG APP_VERSION=dev
ENV NODE_ENV=production \
    PORT=4000 \
    APP_VERSION=${APP_VERSION}
WORKDIR /app

COPY --from=prod-deps --chown=node:node /app/node_modules ./node_modules
COPY --from=build --chown=node:node /app/dist ./dist
COPY --chown=node:node package.json ./

# The official node image ships an unprivileged `node` user; never run the app as root.
USER node
EXPOSE 4000

# busybox wget is available in alpine; curl is not.
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/health" > /dev/null || exit 1

CMD ["node", "dist/main.js"]
