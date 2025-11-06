# Read https://ktor.io/docs/docker.html#build-run
# Stage 1: Cache Gradle dependencies
FROM gradle:8.11-jdk21 AS cache
RUN mkdir -p /home/gradle/cache_home
ENV GRADLE_USER_HOME=/home/gradle/cache_home
COPY build.gradle.kts gradle.properties settings.gradle.kts /home/gradle/app/
COPY gradle /home/gradle/app/gradle
WORKDIR /home/gradle/app
RUN gradle dependencies --no-daemon

# Stage 2: Build Application
FROM gradle:8.11-jdk21 AS build
COPY --from=cache /home/gradle/cache_home /home/gradle/.gradle
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Build the fat JAR, Gradle also supports shadow
# and boot JAR by default.
RUN gradle shadowJar --no-daemon

# Stage 3: Create the Runtime Image
FROM amazoncorretto:21 AS runtime
EXPOSE 8080
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*-all.jar /app/smart-dictophone.jar
ENTRYPOINT ["java","-jar","/app/smart-dictophone.jar"]
