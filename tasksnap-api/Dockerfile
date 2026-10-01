# ================== ETAPA 1: compilar con Maven + JDK 17 ==================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests clean package

# ================== ETAPA 2: imagen final, solo el JRE (mas liviana) ======
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/tasksnap-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]