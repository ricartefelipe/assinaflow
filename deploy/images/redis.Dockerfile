FROM redis:7-alpine@sha256:8da19c7399dca900c6822a1cf30f9d2285585e2c4b457ff4095b0f4500054d3f
RUN apk upgrade --no-cache libcrypto3 libssl3

# Sem root: o usuário redis da imagem oficial é dono de /data; nenhuma porta privilegiada é usada.
USER redis
