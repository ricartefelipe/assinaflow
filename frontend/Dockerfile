FROM node:24-alpine AS build
COPY patch-npm-undici.cjs /tmp/patch-npm-undici.cjs
RUN npm install --global npm@12.2.1 --ignore-scripts \
    && node /tmp/patch-npm-undici.cjs
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
RUN npm run build

FROM nginx:1.30.6-alpine
RUN apk upgrade --no-cache brotli-libs \
    && apk add --no-cache 'curl>=8.23.0-r0' 'libcurl>=8.23.0-r0'
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
