ARG BACKEND_BUILD_IMAGE=maven:3.9-eclipse-temurin-17
ARG BACKEND_RUNTIME_IMAGE=eclipse-temurin:17-jre
FROM ${BACKEND_BUILD_IMAGE} AS build
WORKDIR /build
COPY backend/pom.xml .
COPY backend/src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true package
FROM ${BACKEND_RUNTIME_IMAGE}
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/assistant-simple-1.0.0.jar app.jar
COPY data data
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
