# Use Eclipse Temurin JRE 24 as the base image
FROM eclipse-temurin:24-jre

# Set the working directory
WORKDIR /app

# Copy the Spring Boot JAR file
COPY ./target/funny-app-1.0.0.jar app.jar

# Copy entrypoint script
COPY entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

# Expose the default Spring Boot port
EXPOSE 8080

# Use entrypoint script to inject JVM and env variables
ENTRYPOINT ["./entrypoint.sh"]
