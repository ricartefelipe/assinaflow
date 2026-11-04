# golang:1.27.3-alpine
FROM golang@sha256:d200d2dd76b03a99b52651fd1325aff7dcd35422c35c01bac56571b0507f3deb AS gosu-build
WORKDIR /src
ADD --checksum=sha256:cd9719b775dbfedae53923c9b0dc792b66d42c51e0b36652ed6f747fbadc0164 https://codeload.github.com/tianon/gosu/tar.gz/refs/tags/1.19 /tmp/gosu.tar.gz
RUN tar -xzf /tmp/gosu.tar.gz --strip-components=1 \
    && CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/gosu .

# postgres:16-alpine
FROM postgres@sha256:0cefaabd882e42bcb2a2f3dc56d3ce4770560ba62fd7f197f1a2e008fd5731ab
# Pacotes do Alpine com correções publicadas depois da imagem base (OpenSSL, util-linux).
RUN apk upgrade --no-cache
COPY --from=gosu-build /out/gosu /usr/local/bin/gosu
RUN gosu nobody true
