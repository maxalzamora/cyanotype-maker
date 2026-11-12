# syntax=docker/dockerfile:1

# --- build ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src

# Copy build definitions first so dependency resolution gets its own layer and survives
# source edits.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY imaging/build.gradle.kts ./imaging/build.gradle.kts
COPY app/build.gradle.kts ./app/build.gradle.kts
RUN ./gradlew --no-daemon :app:dependencies --configuration runtimeClasspath > /dev/null

COPY imaging/src ./imaging/src
COPY app/src ./app/src
RUN ./gradlew --no-daemon :app:bootJar

# --- runtime -------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY --from=build /src/app/build/libs/cyanotypes.jar ./cyanotypes.jar

USER app
ENV PORT=8080
EXPOSE 8080

# MaxRAMPercentage lets the JVM follow the container's memory limit instead of the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/cyanotypes.jar"]
