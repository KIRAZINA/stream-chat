# Multi-stage Dockerfile for Stream Chat Application
# Stage 1: Build
FROM maven:3.9.14-amazoncorretto-21 AS builder

WORKDIR /build

# Copy only pom.xml first to leverage Docker build cache for dependencies
COPY pom.xml .

# Download dependencies (cached layer - only invalidated when pom.xml changes)
RUN mvn -q -DskipTests -P !dev dependency:go-offline

# Copy source code and build
COPY src src

# Build application. -P !dev excludes the H2 driver from the runtime image.
# -Dmaven.test.skip=true skips both test execution and test compilation for the
# production image — tests are run in CI/CD before the Docker build.
RUN mvn -q -DskipTests -Dmaven.test.skip=true -P !dev clean package

# Stage 2: Runtime
FROM amazoncorretto:21-alpine

WORKDIR /app

# Install curl for healthcheck in Alpine-based image
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy JAR from builder
COPY --from=builder /build/target/stream-chat-*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Set default profile & port
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080

# Run application
CMD ["java", "-jar", "app.jar"]
