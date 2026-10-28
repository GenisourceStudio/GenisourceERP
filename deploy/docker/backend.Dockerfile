# Build stage. The build context is the repository root so Maven settings are shared
# with local builds. Use MAVEN_MIRROR=aliyun in mainland China.
FROM maven:3.9-eclipse-temurin-21 AS build

ARG MAVEN_MIRROR=central
WORKDIR /build

COPY deploy/maven/settings-central.xml deploy/maven/settings-aliyun.xml /build/

# POMs first so the dependency layer is cached until a dependency actually changes.
# Every module needs its own line: the parent POM lists them as children, and Maven
# refuses to read the reactor at all if one of them is missing.
COPY backend/pom.xml /build/pom.xml
COPY backend/platform/pom.xml /build/platform/pom.xml
COPY backend/masterdata/pom.xml /build/masterdata/pom.xml
COPY backend/inventory/pom.xml /build/inventory/pom.xml
COPY backend/finance/pom.xml /build/finance/pom.xml
COPY backend/purchase/pom.xml /build/purchase/pom.xml
COPY backend/sales/pom.xml /build/sales/pom.xml
COPY backend/production/pom.xml /build/production/pom.xml
COPY backend/mrp/pom.xml /build/mrp/pom.xml
COPY backend/integration/pom.xml /build/integration/pom.xml
COPY backend/bootstrap/pom.xml /build/bootstrap/pom.xml
RUN mvn -B -q -s /build/settings-${MAVEN_MIRROR}.xml -DskipTests \
        -pl bootstrap -am dependency:resolve || true

# Sources second: changing a Java file must not re-download the world.
COPY backend/platform/src /build/platform/src
COPY backend/masterdata/src /build/masterdata/src
COPY backend/inventory/src /build/inventory/src
COPY backend/finance/src /build/finance/src
COPY backend/purchase/src /build/purchase/src
COPY backend/sales/src /build/sales/src
COPY backend/production/src /build/production/src
COPY backend/mrp/src /build/mrp/src
COPY backend/integration/src /build/integration/src
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
