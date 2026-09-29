FROM node:20-alpine AS build

WORKDIR /app

COPY shining-ui/package.json shining-ui/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY shining-ui/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
