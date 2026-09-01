FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY services/pom.xml services/pom.xml
COPY services/sfl-service-common services/sfl-service-common
COPY services/sfl-safety-security-service services/sfl-safety-security-service
RUN mvn -f services/pom.xml -pl sfl-safety-security-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/logs
COPY --from=build /workspace/services/sfl-safety-security-service/target/sfl-safety-security-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
# GC logging on by default (rotated, capped at 100MB total) so a HikariCP "thread starvation or
# clock leap" warning - or any other stall - can be checked against real pause data instead of
# guessed at after the fact. No -Xmx/-Xms pinned: JDK 17 reads the container's cgroup memory limit
# itself (MaxRAMPercentage, default 25%) - set docker/orchestrator memory limits deliberately rather
# than duplicating them here, or the two can silently disagree.
ENTRYPOINT ["java", "-Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m", "-jar", "app.jar"]