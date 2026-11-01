FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace/app

# Copy pom.xml and source code
COPY pom.xml .
COPY src src

# Build the application
RUN ./mvnw install -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
VOLUME /tmp
COPY --from=build /workspace/app/target/*.jar app.jar
USER appuser:appgroup
ENTRYPOINT ["java","-jar","/app.jar"]
