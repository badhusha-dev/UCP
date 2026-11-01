FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY analytics-service/target/*.jar /app/app.jar
USER appuser:appgroup
EXPOSE 8089
ENTRYPOINT ["java","-jar","/app/app.jar"]
