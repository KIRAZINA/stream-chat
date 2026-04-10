# Multi-stage Dockerfile for Stream Chat Application
# Stage 1: Build
FROM maven:3.9.14-amazoncorretto-21 AS builder

WORKDIR /build

# Copy project files
COPY pom.xml .
COPY src src

# Build application
RUN mvn -q -DskipTests clean package

# Stage 2: Runtime
FROM amazoncorretto:21-minimal

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/target/stream-chat-*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD java -cp app.jar org.springframework.boot.loader.JarLauncher -cp 'app.jar' -m 'com.streamchat.StreamChatApplication' &>/dev/null || exit 1

# Set default profile to prod
ENV SPRING_PROFILES_ACTIVE=prod

# Set default port
ENV SERVER_PORT=8080

# Run application
CMD ["java", "-jar", "app.jar"]
