FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/torrentbot-0.0.1-SNAPSHOT.jar /app/torrentbot.jar
ENTRYPOINT ["java", "-jar", "/app/torrentbot.jar"]
