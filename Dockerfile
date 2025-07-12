# Build stage
FROM gradle:jdk21 AS builder
WORKDIR /app

# Copy gradle files first for better layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY buildSrc buildSrc

# Download dependencies
RUN gradle dependencies --no-daemon

# Copy source code
COPY ./ keruta-executor

# Build the application
RUN gradle :keruta-executor:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built jar file from the builder stage
COPY --from=builder /app/keruta-executor/build/libs/*.jar app.jar

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=prod

# Expose the application port
EXPOSE 8081

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
