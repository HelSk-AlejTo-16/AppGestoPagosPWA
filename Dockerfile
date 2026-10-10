FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace
COPY . .

RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon bootJar \
    && mkdir -p /opt/app \
    && JAR_PATH="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "$JAR_PATH" \
    && cp "$JAR_PATH" /opt/app/app.jar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build --chown=10001:10001 /opt/app/app.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
EXPOSE 8080
USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
