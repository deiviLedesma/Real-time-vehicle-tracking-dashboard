FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY . .

RUN mvn -pl GerenciaProductos -am package dependency:copy-dependencies -DincludeScope=runtime -DskipTests -q

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/GerenciaProductos/target/*.jar app.jar

ENTRYPOINT ["java", "-cp", "app.jar", "com.mycompany.gerenciaproductos.GerenciaProductos"]
