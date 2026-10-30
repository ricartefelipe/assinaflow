FROM rabbitmq:3-management-alpine
RUN apk upgrade --no-cache libcrypto3 libssl3 libexpat musl musl-utils python3 pyc zlib

# Sem root: a imagem oficial já possui o usuário rabbitmq dono de /var/lib/rabbitmq e /etc/rabbitmq;
# o entrypoint só usa gosu quando parte de root, então o processo nasce e permanece sem privilégio.
USER rabbitmq
