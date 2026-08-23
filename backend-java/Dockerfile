# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Dmaven.test.skip=true dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --create-home app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Dfile.encoding=UTF-8 -XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0"
EXPOSE 8080
USER app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
