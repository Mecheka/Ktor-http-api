FROM gradle:8.6.0-jdk17 AS build
COPY . /appbuild
WORKDIR /appbuild
RUN gradle clean build

FROM openjdk:17
EXPOSE 8080:8080
RUN mkdir /app
COPY --from=build /appbuild/build/libs/*.jar /app/ktor-docker-sample.jar
ENTRYPOINT ["java","-jar","/app/ktor-docker-sample.jar"]