# Use Eclipse Temurin JRE 24 as the base image
FROM eclipse-temurin:24-jre

# Set the working directory
WORKDIR /app

# Install curl
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*
# Copy the Spring Boot JAR file
COPY target/funny-app-1.0.0.jar app.jar

# Copy entrypoint script
COPY entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

# Expose the default Spring Boot port
EXPOSE 8081

# Use entrypoint script to inject JVM and env variables
ENTRYPOINT ["./entrypoint.sh"]
