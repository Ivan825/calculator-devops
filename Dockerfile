# ---------- Stage 1: build the executable JAR with Maven + JDK ----------
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only the POM first so the dependency layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: slim JRE runtime that contains only the JAR ----------
FROM eclipse-temurin:21-jre-alpine

ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="calculator-devops" \
      org.opencontainers.image.description="Calculator REST API (Spring Boot)" \
      org.opencontainers.image.source="https://github.com/Ivan825/calculator-devops" \
      org.opencontainers.image.version="${APP_VERSION}"

# Run as an unprivileged user
RUN addgroup -S spring && adduser -S -G spring -H spring

WORKDIR /app
# JAR stays root-owned and read-only for the runtime user
COPY --from=build /workspace/target/calculator.jar app.jar

USER spring:spring

ENV APP_VERSION=${APP_VERSION} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
