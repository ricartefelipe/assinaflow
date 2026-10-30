FROM maven:3.9.16-eclipse-temurin-21 AS build
# Java/Maven run directly; the unused base-image service supervisor is omitted.
RUN if [ -f /usr/bin/pebble ]; then rm /usr/bin/pebble; fi
WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -q -DskipTests package
# O Maven da imagem embute guava 33.6.0 (CVE-2026-102554); a correção publicada é 33.7.2, trocada
# pelo artefato oficial com SHA-256 conferido. Pacotes do SO recebem as correções disponíveis.
ADD --checksum=sha256:b530942257fb935f8b2cfaa5f8eb5bd59c555fd8e8d01b8ce98912e077ea606c \
    https://repo1.maven.org/maven2/com/google/guava/guava/33.7.2-jre/guava-33.7.2-jre.jar /usr/share/maven/lib/guava-33.7.2-jre.jar
RUN rm -f /usr/share/maven/lib/guava-33.6.0-jre.jar \
    && apt-get update && apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

FROM eclipse-temurin:21-jre
# Java/Maven run directly; the unused base-image service supervisor is omitted.
RUN if [ -f /usr/bin/pebble ]; then rm /usr/bin/pebble; fi \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app app
WORKDIR /app

COPY --from=build --chown=app:app /app/target/*.jar /app/app.jar

# Sem privilegios: a JVM nao precisa de root.
USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
