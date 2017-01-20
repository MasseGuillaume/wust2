FROM openjdk:8-jre-alpine

ARG version

COPY "backend/target/scala-2.11/backend-assembly-$version.jar" /app/backend.jar

WORKDIR /app
ENTRYPOINT java -jar backend.jar