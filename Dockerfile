FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN apt-get update && apt-get install -y tzdata
ENV TZ=Europe/Istanbul

ENTRYPOINT ["java", "-jar", "app.jar"]
