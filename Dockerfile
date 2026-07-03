# Stage 1 - Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
COPY src src
RUN ./mvnw clean package -DskipTests --no-transfer-progress

#Stage 2 - Run
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/leavesync-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-Xms64m", "-Xmx256m", "-jar", "app.jar"]