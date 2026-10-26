FROM rabbitmq:3-management-alpine
RUN apk upgrade --no-cache libcrypto3 libssl3 libexpat musl musl-utils python3 pyc zlib
