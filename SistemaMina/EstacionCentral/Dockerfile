FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY . .

RUN mvn -pl EstacionCentral -am package dependency:copy-dependencies -DincludeScope=runtime -DskipTests -q

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/EstacionCentral/target/*.jar app.jar
COPY --from=build /workspace/EstacionCentral/target/dependency ./dependency

ENTRYPOINT ["java", "-cp", "app.jar:dependency/*", "com.mycompany.estacioncentral.EstacionCentral"]
