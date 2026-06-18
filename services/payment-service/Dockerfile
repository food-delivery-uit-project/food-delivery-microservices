# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Download OpenTelemetry Java Agent
FROM eclipse-temurin:21-jre-alpine AS otel-agent
RUN wget -q -O /otel-agent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.4.0/opentelemetry-javaagent.jar

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
COPY --from=otel-agent /otel-agent.jar otel-agent.jar
EXPOSE 8080

# OTel agent is activated via JAVA_TOOL_OPTIONS env var.
# In K8s, OTEL_EXPORTER_OTLP_ENDPOINT is set via Helm values to point to the OTel Collector.
# In Docker Compose, it can point to http://jaeger:4317 directly.
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/otel-agent.jar"

ENTRYPOINT ["java", "-jar", "app.jar"]
