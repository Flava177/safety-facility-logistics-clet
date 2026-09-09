FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY services/pom.xml services/pom.xml
COPY services/sfl-service-common services/sfl-service-common
COPY services/sfl-fleet-logistics-service services/sfl-fleet-logistics-service
RUN mvn -f services/pom.xml -pl sfl-fleet-logistics-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/services/sfl-fleet-logistics-service/target/sfl-fleet-logistics-service-0.1.0-SNAPSHOT.jar app.jar
# Matches application.yml's own default (server.port: ${SFL_PORT:8093}) so a container run with no
# override listens on the port it documents. A deployment that needs a different port - the
# microservices compose file sets 8080 - overrides SFL_PORT at run time, which takes precedence
# over this default.
ENV SFL_PORT=8093
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "app.jar"]