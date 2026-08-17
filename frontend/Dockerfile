# syntax=docker/dockerfile:1.4
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --frozen-lockfile
COPY . .

# Vite build-time env. Defaults are EMPTY -> the SPA uses same-origin /api and
# derives the WS URL from window.location, and nginx proxies both to the
# backend. Override with docker build --build-arg VITE_API_URL=... for custom
# deployments. Vite gives process.env VITE_* vars priority over .env.production.
ARG VITE_API_URL=
ARG VITE_WS_URL=
ARG VITE_APP_VERSION=1.0.0
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
