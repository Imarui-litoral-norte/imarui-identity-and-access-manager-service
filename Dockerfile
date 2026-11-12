# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
# Full integration tests run in GitHub Actions against PostgreSQL.
RUN mvn -B -ntp -DskipTests clean package \
    && cp target/imarui-iam-service-0.0.1-SNAPSHOT.jar /tmp/iiam.jar

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 iiam \
    && useradd --uid 10001 --gid 10001 --system --no-create-home --shell /usr/sbin/nologin iiam
WORKDIR /app
COPY --from=builder --chown=10001:10001 /tmp/iiam.jar /app/iiam.jar
ENV SPRING_PROFILES_ACTIVE=prod
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/iiam.jar"]
