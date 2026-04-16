# ─────────────────────────────────────────────
# Stage 1: Build with Maven
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN apk add --no-cache maven && mvn clean package -DskipTests

# ─────────────────────────────────────────────
# Stage 2: Run with slim JRE
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/ev-routing-service-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
