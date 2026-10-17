ARG FRONTEND_BUILD_IMAGE=node:24-alpine
ARG FRONTEND_RUNTIME_IMAGE=nginx:1.28-alpine
FROM ${FRONTEND_BUILD_IMAGE} AS build
WORKDIR /build
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build
FROM ${FRONTEND_RUNTIME_IMAGE}
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
