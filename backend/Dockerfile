FROM maven:3.9.16-eclipse-temurin-21 AS build
# Java/Maven run directly; the unused base-image service supervisor is omitted.
RUN if [ -f /usr/bin/pebble ]; then rm /usr/bin/pebble; fi
WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -q -DskipTests package
RUN apt-get update && apt-get install -y --only-upgrade --no-install-recommends libssl3t64 openssl \
    && rm -rf /var/lib/apt/lists/*

FROM eclipse-temurin:21-jre
# Java/Maven run directly; the unused base-image service supervisor is omitted.
RUN if [ -f /usr/bin/pebble ]; then rm /usr/bin/pebble; fi
WORKDIR /app

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
