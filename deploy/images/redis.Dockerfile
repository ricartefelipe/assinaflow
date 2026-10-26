FROM redis:7-alpine
RUN apk upgrade --no-cache libcrypto3 libssl3
