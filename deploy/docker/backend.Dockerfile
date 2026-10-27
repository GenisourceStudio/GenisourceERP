# Build stage. The build context is the repository root so Maven settings are shared
# with local builds. Use MAVEN_MIRROR=aliyun in mainland China.
FROM maven:3.9-eclipse-temurin-21 AS build

ARG MAVEN_MIRROR=central
WORKDIR /build

COPY deploy/maven/settings-central.xml deploy/maven/settings-aliyun.xml /build/

# POMs first so the dependency layer is cached until a dependency actually changes.
COPY backend/pom.xml /build/pom.xml
COPY backend/platform/pom.xml /build/platform/pom.xml
COPY backend/bootstrap/pom.xml /build/bootstrap/pom.xml
RUN mvn -B -q -s /build/settings-${MAVEN_MIRROR}.xml -DskipTests \
        -pl platform,bootstrap -am dependency:resolve || true

COPY backend/platform/src /build/platform/src
COPY backend/bootstrap/src /build/bootstrap/src
RUN mvn -B -q -s /build/settings-${MAVEN_MIRROR}.xml -DskipTests package

# Runtime stage: small JRE, non-root user.
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S erp && adduser -S erp -G erp
WORKDIR /app
COPY --from=build /build/bootstrap/target/genisource-erp.jar /app/app.jar

USER erp
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
