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
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gnupg \
    && curl https://packages.microsoft.com/keys/microsoft.asc | apt-key add - \
    && curl https://packages.microsoft.com/config/ubuntu/22.04/mssql-server-2022.list | tee /etc/apt/sources.list.d/mssql-server.list \
    && apt-get update \
    && ACCEPT_EULA=Y apt-get install -y mssql-server \
    && rm -rf /var/lib/apt/lists/*

# Non-root user for the application
RUN groupadd --system spring && useradd --system --gid spring spring

# Copy built jar
COPY --from=builder /app/target/*.jar app.jar
RUN chown spring:spring app.jar

# Expose application and database ports
EXPOSE 8080 1433

# Default SQL Server configuration
ENV ACCEPT_EULA=Y \
    MSSQL_SA_PASSWORD=YourStrong!Passw0rd \
    MSSQL_PID=Express \
    JAVA_OPTS="-Xmx512m -Xms256m -Djava.security.egd=file:/dev/./urandom"

# Health check (requires Spring Boot Actuator)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Run SQL Server in background then start the app as non-root user
ENTRYPOINT ["sh", "-c", "/opt/mssql/bin/sqlservr & su spring -c 'java $JAVA_OPTS -jar app.jar'"]
