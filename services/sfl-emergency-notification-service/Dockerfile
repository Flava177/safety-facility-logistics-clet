FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY services/pom.xml services/pom.xml
COPY services/sfl-service-common services/sfl-service-common
COPY services/sfl-emergency-notification-service services/sfl-emergency-notification-service
RUN mvn -f services/pom.xml -pl sfl-emergency-notification-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/services/sfl-emergency-notification-service/target/sfl-emergency-notification-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
