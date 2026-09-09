FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY services/pom.xml services/pom.xml
COPY services/sfl-service-common services/sfl-service-common
COPY services/sfl-facilities-service services/sfl-facilities-service
# `-pl X -am` only builds this module and what it depends on, but Maven still parses every <module>
# the parent POM declares to construct the reactor graph before applying that filter - so every
# sibling module needs at least its own pom.xml present, even though none of its source is built here
# or ends up in the final image. Verified: a full source COPY is not required for this, just the POM.
COPY services/sfl-safety-security-service/pom.xml services/sfl-safety-security-service/pom.xml
COPY services/sfl-fleet-logistics-service/pom.xml services/sfl-fleet-logistics-service/pom.xml
COPY services/sfl-portal-service/pom.xml services/sfl-portal-service/pom.xml
RUN mvn -f services/pom.xml -pl sfl-facilities-service -am package -DskipTests

FROM eclipse-temurin:17-jre
RUN groupadd -r spring && useradd -r -g spring spring
WORKDIR /app
COPY --from=build /workspace/services/sfl-facilities-service/target/sfl-facilities-service-0.1.0-SNAPSHOT.jar app.jar
RUN chown -R spring:spring /app
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]