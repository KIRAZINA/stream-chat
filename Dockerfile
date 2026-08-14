# Multi-stage Dockerfile for Stream Chat Application
# Stage 1: Build
FROM maven:3.9.14-amazoncorretto-21 AS builder

WORKDIR /build

# Copy project files
COPY pom.xml .
COPY src src

# Build application. -P !dev excludes the H2 driver from the runtime image.
RUN mvn -q -DskipTests -P !dev clean package

# Stage 2: Runtime
# Amazon Corretto minimal image: install curl for healthcheck
RUN microdnf install -y curl && microdnf clean all

FROM amazoncorretto:21-minimal

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/target/stream-chat-*.jar app.jar

# Expose port
EXPOSE 8080

# Health check (uses curl now installed)
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Set default profile to prod
ENV SPRING_PROFILES_ACTIVE=prod

# Set default port
ENV SERVER_PORT=8080

# Run application
CMD ["java", "-jar", "app.jar"]
