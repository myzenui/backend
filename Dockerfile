# syntax=docker/dockerfile:1.7

############################
# Stage 1 — Build (JDK 21) #
############################
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Copy wrapper + pom first for better caching
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY .mvn .mvn
COPY pom.xml pom.xml

# Ensure wrapper is executable (important in Linux containers)
RUN chmod +x mvnw

# Copy sources
COPY src src

# Build the application
RUN ./mvnw package -DskipTests

##################################
# Stage 2 — Runtime (JRE 21)     #
##################################
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Optional: curl for health checks
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Non-root user
RUN groupadd --system spring && useradd --system --gid spring spring

# Copy built jar
COPY --from=builder /app/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

# Expose app port
EXPOSE 8080

# Health check (requires Spring Boot Actuator)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m -Djava.security.egd=file:/dev/./urandom"

# Run
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
