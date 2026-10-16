FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /work
COPY --from=build --chown=10001:0 /workspace/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=10001:0 /workspace/target/quarkus-app/*.jar ./
COPY --from=build --chown=10001:0 /workspace/target/quarkus-app/app/ ./app/
COPY --from=build --chown=10001:0 /workspace/target/quarkus-app/quarkus/ ./quarkus/
USER 10001:0
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/work/quarkus-run.jar"]
