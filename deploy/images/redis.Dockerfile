FROM redis:7-alpine
RUN apk upgrade --no-cache libcrypto3 libssl3

# Sem root: o usuário redis da imagem oficial é dono de /data; nenhuma porta privilegiada é usada.
USER redis
