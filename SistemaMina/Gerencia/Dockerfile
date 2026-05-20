FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY . .

RUN mvn -pl Gerencia -am package dependency:copy-dependencies -DincludeScope=runtime -DskipTests -q

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/Gerencia/target/*.jar app.jar
COPY --from=build /workspace/Gerencia/target/dependency ./dependency

ENTRYPOINT ["java", "-cp", "app.jar:dependency/*", "com.mycompany.gerencia.Gerencia"]
