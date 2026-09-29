FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY music-service/pom.xml music-service/pom.xml
COPY community-service/pom.xml community-service/pom.xml
COPY statistics-service/pom.xml statistics-service/pom.xml
COPY recommend-service/pom.xml recommend-service/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline -DskipTests

COPY common/src common/src
COPY gateway-service/src gateway-service/src
COPY user-service/src user-service/src
COPY music-service/src music-service/src
COPY community-service/src community-service/src
COPY statistics-service/src statistics-service/src
COPY recommend-service/src recommend-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /workspace/gateway-service/target/gateway-service-1.0-SNAPSHOT.jar /app/gateway-service.jar
COPY --from=build /workspace/user-service/target/user-service-1.0-SNAPSHOT.jar /app/user-service.jar
COPY --from=build /workspace/music-service/target/music-service-1.0-SNAPSHOT.jar /app/music-service.jar
COPY --from=build /workspace/community-service/target/community-service-1.0-SNAPSHOT.jar /app/community-service.jar
COPY --from=build /workspace/statistics-service/target/statistics-service-1.0-SNAPSHOT.jar /app/statistics-service.jar
COPY --from=build /workspace/recommend-service/target/recommend-service-1.0-SNAPSHOT.jar /app/recommend-service.jar

ENTRYPOINT ["java", "-jar"]

