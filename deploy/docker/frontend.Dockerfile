# Build the SPA, then serve it from Nginx which also proxies the API.
FROM node:22-alpine AS build

# Registry can be overridden for mainland China (e.g. https://registry.npmmirror.com).
ARG NPM_REGISTRY=https://registry.npmjs.org
WORKDIR /app
RUN npm install -g pnpm@10.32.1 --registry=${NPM_REGISTRY}

COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm config set registry ${NPM_REGISTRY} && pnpm install --frozen-lockfile

COPY frontend/ ./
RUN pnpm build

FROM nginx:stable-alpine

COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
