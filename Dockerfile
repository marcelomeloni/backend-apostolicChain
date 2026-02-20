# Estágio 1: Build do projeto usando Maven
FROM maven:3.8.5-openjdk-17 AS build
COPY . .
RUN mvn clean package -DskipTests

# Estágio 2: Execução do JAR
FROM openjdk:17-jdk-slim
COPY --from=build /target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]