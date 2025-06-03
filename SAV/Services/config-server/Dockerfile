# Use an official OpenJDK runtime as a parent image
FROM openjdk:17-jdk-slim AS build

# Set the working directory
WORKDIR /app

# Copy the Maven wrapper and the pom.xml
# Ensure mvnw has execute permissions
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw

# Download dependencies (this layer will be cached)
# Use -B for batch mode to avoid interactive prompts
RUN ./mvnw dependency:go-offline -B

# Copy the project source
COPY src src

# Package the application, skipping tests for faster builds
RUN ./mvnw package -DskipTests -B

# Use a smaller base image for the final stage
FROM openjdk:17-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the packaged jar file from the build stage
# The wildcard (*) handles potential variations in the artifact name
COPY --from=build /app/target/*.jar app.jar

# Expose the port the application runs on (default 8888 for Spring Cloud Config Server)
EXPOSE 8881

# Define the command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

