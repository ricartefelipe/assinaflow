FROM rabbitmq:4.4.0-management-alpine@sha256:b768d2e09a809b48458d6c3c32fc2894643a6bfafd76264c120373eff0ee6532
# Correções do Alpine publicadas depois da imagem base entram em cada build.
RUN apk upgrade --no-cache

# Sem root: a imagem oficial já possui o usuário rabbitmq dono de /var/lib/rabbitmq e /etc/rabbitmq;
# o entrypoint só usa gosu quando parte de root, então o processo nasce e permanece sem privilégio.
USER rabbitmq
