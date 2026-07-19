# syntax=docker/dockerfile:1.7
# Platform Management Service — frontend (React SPA)

FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Build-time Entra / API public config (bake into SPA)
ARG APP_AZURE_TENANT_ID
ARG APP_AZURE_CLIENT_ID
ARG APP_AZURE_API_CLIENT_ID
ARG APP_AZURE_API_SCOPE
ARG APP_API_BASE_URL=

ENV APP_AZURE_TENANT_ID=$APP_AZURE_TENANT_ID \
    APP_AZURE_CLIENT_ID=$APP_AZURE_CLIENT_ID \
    APP_AZURE_API_CLIENT_ID=$APP_AZURE_API_CLIENT_ID \
    APP_AZURE_API_SCOPE=$APP_AZURE_API_SCOPE \
    APP_API_BASE_URL=$APP_API_BASE_URL

RUN npm run build

FROM nginx:1.27-alpine AS runtime
LABEL org.opencontainers.image.title="Platform Management Service UI" \
      org.opencontainers.image.source="https://github.com/juminrubin/platform-management-service"

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
