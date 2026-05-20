FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY . .

RUN mvn -pl Notificaciones -am package -DskipTests -q

FROM eclipse-temurin:21-jre

WORKDIR /deployments

COPY --from=build /workspace/Notificaciones/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build /workspace/Notificaciones/target/quarkus-app/*.jar /deployments/
COPY --from=build /workspace/Notificaciones/target/quarkus-app/app/ /deployments/app/
COPY --from=build /workspace/Notificaciones/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8083

ENTRYPOINT ["java", "-Dquarkus.http.host=0.0.0.0", "-jar", "/deployments/quarkus-run.jar"]
