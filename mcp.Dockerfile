# Build stage
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

COPY gradlew gradlew.bat* settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY submodules/ submodules/

RUN chmod +x ./gradlew && ./gradlew :server:shadowJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /app/submodules/server/build/libs/crml-mcp-server.jar app.jar

EXPOSE 63029

ENTRYPOINT ["java", "-jar", "app.jar"]
