  # Multi-stage build for optimized image size

  # Stage 1: Build
  FROM eclipse-temurin:17-jdk-alpine AS build
  WORKDIR /app

  # Copy Maven wrapper and pom.xml
  COPY .mvn/ .mvn
  COPY mvnw pom.xml ./

  # Download dependencies (cached layer)
  RUN ./mvnw dependency:go-offline

  # Copy source code
  COPY src ./src

  # Build application
  RUN ./mvnw clean package -DskipTests

  # Stage 2: Runtime
  FROM eclipse-temurin:17-jre-alpine
  WORKDIR /app

  # Create non-root user
  RUN addgroup -S spring && adduser -S spring -G spring
  USER spring:spring

  # Copy jar from build stage
  COPY --from=build /app/target/*.jar app.jar

  # Expose port
  EXPOSE 8080

  # Run application
  ENTRYPOINT ["java", "-jar", "app.jar"]
