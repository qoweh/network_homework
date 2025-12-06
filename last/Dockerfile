# Network Chat Application - Docker Container
# Multi-stage build for optimized image size

# ============== Build Stage ==============
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy project files
COPY pom.xml .
COPY lib/ lib/
COPY src/ src/

# Build the application (skip tests as they require native pcap)
RUN mvn clean package -DskipTests -Dmaven.compiler.forceJavacCompilerUse=true

# ============== Runtime Stage ==============
FROM eclipse-temurin:21-jre

LABEL maintainer="Network Chat App"
LABEL description="Network Protocol Stack Chat Application with Encryption, Priority Queue, and Timestamp features"
LABEL version="1.0"

WORKDIR /app

# Install libpcap for network packet capture (optional for GUI mode)
RUN apt-get update && \
    apt-get install -y libpcap-dev && \
    rm -rf /var/lib/apt/lists/*

# Copy built JAR and dependencies
COPY --from=builder /app/target/*.jar app.jar
COPY --from=builder /app/lib/ lib/

# Copy run script
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Create directory for received files
RUN mkdir -p /app/received_files

# Environment variables
ENV JAVA_OPTS="--enable-preview"
ENV APP_MODE="demo"

# Expose port for potential network communication
EXPOSE 5000

ENTRYPOINT ["/docker-entrypoint.sh"]
