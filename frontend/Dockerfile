# node:24-alpine
FROM node@sha256:8a32c8cf649a49915a66e81e7a609223c708d0adabf288ee3f4077d765542f85 AS build
COPY patch-npm-undici.cjs /tmp/patch-npm-undici.cjs
RUN npm install --global npm@12.2.1 --ignore-scripts \
    && node /tmp/patch-npm-undici.cjs
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
RUN npm run build

# Variante sem root do nginx oficial: processo mestre e workers como usuario nginx (porta 8080).
# nginxinc/nginx-unprivileged:1.30-alpine
FROM nginxinc/nginx-unprivileged@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
USER root
RUN apk upgrade --no-cache \
    && apk add --no-cache 'curl>=8.23.0-r0' 'libcurl>=8.23.0-r0'
USER nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
