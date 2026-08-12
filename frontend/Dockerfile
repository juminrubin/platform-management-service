# syntax=docker/dockerfile:1.7
# Platform Management Service — frontend (React SPA)
# APP_* Entra/API settings are injected at container start, not baked at build.

FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
LABEL org.opencontainers.image.title="Platform Management Service UI" \
      org.opencontainers.image.source="https://github.com/juminrubin/platform-management-service"

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
ENTRYPOINT ["/docker-entrypoint.sh"]
