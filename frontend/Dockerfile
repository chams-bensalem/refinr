# syntax=docker/dockerfile:1
# Refinr frontend: static build served by an unprivileged nginx (multi-arch: linux/amd64, linux/arm64).

# --- Build: run on the build machine's own architecture (the output is plain static files) ---
FROM --platform=$BUILDPLATFORM node:26-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# The dev placeholder public/config.js is replaced at runtime by the /config.js location of nginx
RUN npm run build && rm -f dist/config.js

# --- Runtime ---
FROM nginxinc/nginx-unprivileged:stable-alpine
# Only REFINR_* variables are substituted in the template (nginx's own $variables stay untouched)
ENV NGINX_ENVSUBST_FILTER=REFINR_ \
    REFINR_BACKEND_URL=http://backend:8080 \
    REFINR_API_BASE_URL=/api/v1 \
    REFINR_API_KEY= \
    REFINR_ALLOWED_HOSTS="localhost,127.0.0.1,[::1]"
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chmod=0755 docker/05-refinr-server-names.envsh /docker-entrypoint.d/05-refinr-server-names.envsh
COPY --from=build /app/dist /usr/share/nginx/html
USER 101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
