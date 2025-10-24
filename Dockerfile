FROM maven:3.9.6-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM openjdk:8-jre-slim
WORKDIR /app
COPY --from=build /workspace/target/redis-benchmark-1.0.0-SNAPSHOT-jar-with-dependencies.jar app.jar
ENV REDIS_URI=redis://redis-benchmark-redis:6379
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
