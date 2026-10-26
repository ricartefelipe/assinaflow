FROM golang:1.27.3-alpine AS gosu-build
WORKDIR /src
ADD --checksum=sha256:cd9719b775dbfedae53923c9b0dc792b66d42c51e0b36652ed6f747fbadc0164 https://codeload.github.com/tianon/gosu/tar.gz/refs/tags/1.19 /tmp/gosu.tar.gz
RUN tar -xzf /tmp/gosu.tar.gz --strip-components=1 \
    && CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/gosu .

FROM postgres:16-alpine
COPY --from=gosu-build /out/gosu /usr/local/bin/gosu
RUN gosu nobody true
